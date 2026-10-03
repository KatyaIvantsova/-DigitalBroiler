package com.broiler_monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class BroilerMonitoringApplication {

	/** Часовой пояс площадки по умолчанию; переопределяется переменной APP_TIMEZONE. */
	public static final String DEFAULT_TIMEZONE = "Europe/Samara";

	public static void main(String[] args) {
		// Время инцидентов, задач и истории хранится как LocalDateTime: и Clock, и LocalDateTime.now()
		// должны работать в одном поясе, иначе время реакции на сервере в UTC «уезжает» на 4 часа.
		String zone = System.getenv().getOrDefault("APP_TIMEZONE", DEFAULT_TIMEZONE);
		TimeZone.setDefault(TimeZone.getTimeZone(zone));
		SpringApplication.run(BroilerMonitoringApplication.class, args);
	}

}
