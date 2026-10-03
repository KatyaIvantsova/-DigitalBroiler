package com.broiler_monitoring.integration.mqtt;

import com.broiler_monitoring.Telemetry.Sensor;
import com.broiler_monitoring.Telemetry.SensorRepository;
import com.broiler_monitoring.Telemetry.TelemetryService;
import com.broiler_monitoring.Telemetry.dto.TelemetryBatchRequest;
import com.broiler_monitoring.Telemetry.dto.TelemetryReadingRequest;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MQTT-шлюз приёма телеметрии (S4-06, docs/sprint2/09-integration-protocol.md п. 2). Подписывается на топики
 * {@code broiler/<site>/<house>/<sensorCode>}, копит показания до {@code mqtt.batch-size} штук или
 * {@code mqtt.flush-interval-ms} и сохраняет их тем же {@link TelemetryService}, что и HTTP-приём.
 * Показание неизвестного, выключенного датчика или с чужим типом отбрасывается с записью в лог — остальные
 * показания пачки сохраняются. Потеря брокера не роняет backend: адаптер переподключается сам.
 * Включается переменной MQTT_ENABLED=true.
 */
@Component
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttTelemetryAdapter implements MqttCallbackExtended, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MqttTelemetryAdapter.class);
    private static final int QOS = 1;

    private final TelemetryService telemetry;
    private final SensorRepository sensors;
    private final Clock clock;
    private final String url;
    private final String topic;
    private final String clientId;
    private final String username;
    private final String password;
    private final int batchSize;

    private final ConcurrentLinkedQueue<MqttPayloadParser.Reading> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger buffered = new AtomicInteger();
    private final AtomicLong saved = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private volatile MqttClient client;
    private volatile boolean running;

    public MqttTelemetryAdapter(TelemetryService telemetry, SensorRepository sensors, Clock clock,
                                @Value("${mqtt.url:tcp://localhost:1883}") String url,
                                @Value("${mqtt.topic:broiler/#}") String topic,
                                @Value("${mqtt.client-id:broiler-backend}") String clientId,
                                @Value("${mqtt.username:}") String username,
                                @Value("${mqtt.password:}") String password,
                                @Value("${mqtt.batch-size:200}") int batchSize) {
        this.telemetry = telemetry;
        this.sensors = sensors;
        this.clock = clock;
        this.url = url;
        this.topic = topic;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.batchSize = batchSize;
    }

    @Override
    public void start() {
        running = true;
        connect();
    }

    @Override
    public void stop() {
        running = false;
        flush();
        MqttClient current = client;
        if (current != null) {
            try {
                if (current.isConnected()) {
                    current.disconnect();
                }
                current.close();
            } catch (MqttException e) {
                log.debug("MQTT: ошибка при отключении", e);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Первое подключение не удалось (брокер ещё не поднялся) — пробуем снова; дальше Paho переподключается сам. */
    @Scheduled(fixedDelayString = "${mqtt.reconnect-interval-ms:30000}", initialDelayString = "${mqtt.reconnect-interval-ms:30000}")
    public void ensureConnected() {
        if (running && (client == null || !client.isConnected())) {
            connect();
        }
    }

    private synchronized void connect() {
        try {
            if (client == null) {
                client = new MqttClient(url, clientId, new MemoryPersistence());
                client.setCallback(this);
            }
            if (client.isConnected()) {
                return;
            }
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setConnectionTimeout(5);
            if (!username.isBlank()) {
                options.setUserName(username);
                options.setPassword(password.toCharArray());
            }
            client.connect(options);
        } catch (MqttException e) {
            log.warn("MQTT: нет связи с брокером {} ({}), повторим позже", url, e.getMessage());
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        try {
            client.subscribe(topic, QOS);
            log.info("MQTT: {} к {}, подписка {}", reconnect ? "переподключились" : "подключились", serverUri, topic);
        } catch (MqttException e) {
            log.warn("MQTT: не удалось подписаться на {}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT: связь с брокером потеряна ({}), переподключаемся", cause == null ? "?" : cause.getMessage());
    }

    @Override
    public void messageArrived(String messageTopic, MqttMessage message) {
        try {
            buffer.add(MqttPayloadParser.parse(messageTopic, new String(message.getPayload(), StandardCharsets.UTF_8), clock.instant()));
            if (buffered.incrementAndGet() >= batchSize) {
                flush();
            }
        } catch (IllegalArgumentException e) {
            rejected.incrementAndGet();
            log.warn("MQTT: сообщение отброшено: {}", e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // адаптер только читает
    }

    @Scheduled(fixedDelayString = "${mqtt.flush-interval-ms:5000}")
    public synchronized void flush() {
        List<MqttPayloadParser.Reading> batch = new ArrayList<>();
        MqttPayloadParser.Reading next;
        while ((next = buffer.poll()) != null) {
            buffered.decrementAndGet();
            batch.add(next);
        }
        if (batch.isEmpty()) {
            return;
        }
        Map<String, List<TelemetryReadingRequest>> byGateway = new LinkedHashMap<>();
        for (MqttPayloadParser.Reading reading : batch) {
            toRequest(reading).ifPresent(request -> byGateway.computeIfAbsent(reading.gatewayId(), key -> new ArrayList<>()).add(request));
        }
        byGateway.forEach((gateway, readings) -> {
            TelemetryBatchRequest request = new TelemetryBatchRequest();
            request.setGatewayId(gateway);
            request.setReadings(readings);
            try {
                saved.addAndGet(telemetry.ingest(request).getSaved());
            } catch (RuntimeException e) {
                rejected.addAndGet(readings.size());
                log.warn("MQTT: пачка {} из {} показаний не сохранена: {}", gateway, readings.size(), e.getMessage());
            }
        });
    }

    /** Проверка по справочнику датчиков; тип и единицу берём из справочника, если шлюз прислал просто число. */
    private Optional<TelemetryReadingRequest> toRequest(MqttPayloadParser.Reading reading) {
        Optional<Sensor> found = sensors.findByCode(reading.sensorCode());
        String problem = null;
        if (found.isEmpty()) {
            problem = "датчик не заведён в справочнике";
        } else if (!Boolean.TRUE.equals(found.get().getActive())) {
            problem = "датчик выключен";
        } else if (reading.type() != null && reading.type() != found.get().getType()) {
            problem = "тип %s не совпадает со справочником (%s)".formatted(reading.type(), found.get().getType());
        }
        if (problem != null) {
            rejected.incrementAndGet();
            log.warn("MQTT: показание {} отброшено: {}", reading.sensorCode(), problem);
            return Optional.empty();
        }
        Sensor sensor = found.get();
        TelemetryReadingRequest request = new TelemetryReadingRequest();
        request.setSensorCode(sensor.getCode());
        request.setType(sensor.getType());
        request.setValue(reading.value());
        request.setUnit(reading.unit() != null ? reading.unit() : sensor.getUnit());
        request.setMeasuredAt(reading.measuredAt());
        return Optional.of(request);
    }

    public long savedCount() {
        return saved.get();
    }

    public long rejectedCount() {
        return rejected.get();
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }
}
