package com.broiler_monitoring.service;


import com.broiler_monitoring.dto.AssignIncidentRequest;
import com.broiler_monitoring.entity.AppUser;
import com.broiler_monitoring.entity.Incident;
import com.broiler_monitoring.entity.IncidentHistory;
import com.broiler_monitoring.entity.Notification;
import com.broiler_monitoring.enumerated.IncidentPriority;
import com.broiler_monitoring.enumerated.IncidentSource;
import com.broiler_monitoring.enumerated.IncidentStatus;
import com.broiler_monitoring.enumerated.IncidentType;
import com.broiler_monitoring.enumerated.NotificationStatus;
import com.broiler_monitoring.repository.AppUserRepository;
import com.broiler_monitoring.repository.IncidentHistoryRepository;
import com.broiler_monitoring.repository.IncidentRepository;
import com.broiler_monitoring.repository.NotificationRepository;
import com.broiler_monitoring.security.CurrentActor;
import com.broiler_monitoring.enumerated.UserRole;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class IncidentService {

    private static final DateTimeFormatter INCIDENT_CODE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DEFAULT_USER_NAME = "Павел Романов";
    private static final String DEFAULT_USER_ROLE = "Директор по качеству";

    private final IncidentRepository repository;
    private final NotificationRepository notificationRepository;
    private final AppUserRepository userRepository;
    private final IncidentHistoryRepository historyRepository;
    private final AuditService audit;
    private final Clock clock;



    public IncidentService(
            IncidentRepository repository,
            NotificationRepository notificationRepository,
            AppUserRepository userRepository,
            IncidentHistoryRepository historyRepository,
            AuditService audit,
            Clock clock
    ){
        this.repository = repository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.audit = audit;
        this.clock = clock;
    }

    public List<Incident> findAll(){
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Incident getById(UUID id){
        return repository.findById(id)
                .orElseThrow(()->new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Incident with id '%s' not found".formatted(id)));
    }
    public Incident getByCode(String code){
        return repository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Incidetn with code '%s' not found".formatted(code)));
    }
    public Incident create(Incident incidentRequest) {
        Incident request = incidentRequest != null ? incidentRequest : new Incident();
        Incident incident = new Incident();


        incident.setCode(generateIncidentCode());


        incident.setType(request.getType() != null ? request.getType() : IncidentType.OTHER);
        incident.setWorkshop(blankToNull(request.getWorkshop()));
        incident.setHouse(blankToNull(request.getHouse()));
        incident.setZone(blankToNull(request.getZone()));
        incident.setTitle(firstNotBlank(request.getTitle(), buildIncidentTitle(incident)));
        incident.setDescription(request.getDescription());
        incident.setPriority(request.getPriority() != null ? request.getPriority() : IncidentPriority.MEDIUM);
        incident.setSource(request.getSource() != null ? request.getSource() : IncidentSource.MANUAL);
        incident.setResponsible(blankToNull(request.getResponsible()));
        incident.setDecisionComment(blankToNull(request.getDecisionComment()));


        LocalDateTime now = LocalDateTime.now(clock);
        incident.setCreatedAt(now);
        incident.setDetectedAt(now);


        incident.setStatus(IncidentStatus.OPEN);
        incident.setHouseId(request.getHouseId());
        incident.setZoneId(request.getZoneId());
        incident.setFlockId(request.getFlockId());

        Incident saved = repository.save(incident);
        audit.record(AuditService.INCIDENT, saved.getId(), "CREATED", "Создан инцидент %s".formatted(saved.getCode()));
        return saved;
    }

    @Transactional
    public Incident createFromNotification(UUID notificationId, Incident incidentRequest){
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notification with id '%s' not found".formatted(notificationId)));

        if (!repository.findByNotificationId(notificationId).isEmpty()){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Incident for notification with id '%s' already exists".formatted(notificationId));
        }

        Incident incident = new Incident();
        Incident request = incidentRequest != null ? incidentRequest : new Incident();

        incident.setCode(firstNotBlank(request.getCode(), generateIncidentCode()));
        incident.setTitle(firstNotBlank(request.getTitle(), notification.getTitle()));
        incident.setDescription(firstNotBlank(request.getDescription(), notification.getDescription()));
        incident.setPriority(request.getPriority() != null ? request.getPriority() : mapNotificationPriority(notification));
        incident.setType(request.getType() != null ? request.getType() : IncidentType.OTHER);
        incident.setWorkshop(blankToNull(request.getWorkshop()));
        incident.setHouse(blankToNull(request.getHouse()));
        incident.setZone(blankToNull(request.getZone()));
        incident.setSource(IncidentSource.NOTIFICATION);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setNotificationId(notification.getId());
        incident.setResponsible(blankToNull(request.getResponsible()));
        incident.setDecisionComment(blankToNull(request.getDecisionComment()));
        incident.setDetectedAt(request.getDetectedAt() != null ? request.getDetectedAt() : notification.getCreatedAt());

        notification.setStatus(NotificationStatus.INCIDENT_CREATED);
        notificationRepository.save(notification);

        return repository.save(incident);
    }

    public Incident update(UUID id, Incident updatedIncident) {
        Incident incident = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Incident with id '%s' not found".formatted(id)));
        AuditService.Changes changes = AuditService.changes()
                .field("Заголовок", incident.getTitle(), firstNonNull(updatedIncident.getTitle(), incident.getTitle()))
                .field("Тип", incident.getType(), firstNonNull(updatedIncident.getType(), incident.getType()))
                .field("Приоритет", incident.getPriority(), firstNonNull(updatedIncident.getPriority(), incident.getPriority()))
                .field("Ответственный", incident.getResponsible(), firstNonNull(updatedIncident.getResponsible(), incident.getResponsible()))
                .field("Решение", incident.getDecisionComment(), firstNonNull(updatedIncident.getDecisionComment(), incident.getDecisionComment()));

        if (updatedIncident.getTitle() != null) {
            incident.setTitle(updatedIncident.getTitle());
        }
        if (updatedIncident.getDescription() != null) {
            incident.setDescription(updatedIncident.getDescription());
        }
        if (updatedIncident.getType() != null) {
            incident.setType(updatedIncident.getType());
        }
        if (updatedIncident.getPriority() != null) {
            incident.setPriority(updatedIncident.getPriority());
        }
        if (updatedIncident.getWorkshop() != null) {
            incident.setWorkshop(blankToNull(updatedIncident.getWorkshop()));
        }
        if (updatedIncident.getHouse() != null) {
            incident.setHouse(blankToNull(updatedIncident.getHouse()));
        }
        if (updatedIncident.getZone() != null) {
            incident.setZone(blankToNull(updatedIncident.getZone()));
        }
        if (updatedIncident.getResponsible() != null) {
            incident.setResponsible(blankToNull(updatedIncident.getResponsible()));
        }
        if (updatedIncident.getDecisionComment() != null) {
            incident.setDecisionComment(blankToNull(updatedIncident.getDecisionComment()));
        }

        Incident saved = repository.save(incident);
        if (!changes.isEmpty()) {
            audit.record(AuditService.INCIDENT, saved.getId(), "UPDATED", "Изменён инцидент %s".formatted(saved.getCode()), changes);
        }
        return saved;
    }

    public Incident changeStatus(UUID id, IncidentStatus newStatus){
        Incident incident = getById(id);
        IncidentStatus oldStatus = incident.getStatus();

        switch (newStatus) {
            case IN_PROGRESS -> {
                if (oldStatus == IncidentStatus.OPEN && incident.getStartedAt() == null) {
                    incident.setStartedAt(LocalDateTime.now(clock));
                    incident.setReactionMinutes(calcReaction(incident));
                }
                if (oldStatus == IncidentStatus.CLOSED) {
                    incident.setClosedAt(null);
                    incident.setResolvedAt(null);
                    // startedAt НЕ трогаем
                }
            }
            case RESOLVED -> {
                if (incident.getResolvedAt() == null) {
                    incident.setResolvedAt(LocalDateTime.now(clock));
                }
            }
            case CLOSED -> {
                if (incident.getClosedAt() == null) {
                    incident.setClosedAt(LocalDateTime.now(clock));
                }
            }
            default -> {}
        }

        incident.setStatus(newStatus);
        Incident saved = repository.save(incident);

        // ── запись в историю (ТЗ: STATUS_CHANGED при любом переходе) ──
        if (oldStatus != newStatus) {
            CurrentActor actor = CurrentActor.get();
            historyRepository.save(new IncidentHistory(
                    saved.getId(),
                    "STATUS_CHANGED",
                    actor.id(),
                    actor.name(),
                    "Статус изменён: %s → %s".formatted(oldStatus, newStatus)
            ));
            audit.record(AuditService.INCIDENT, saved.getId(), "STATUS_CHANGED",
                    "Инцидент %s: %s → %s".formatted(saved.getCode(), oldStatus, newStatus));
        }

        return saved;
    }

    private Long calcReaction(Incident i) {
        LocalDateTime base = i.getDetectedAt() != null ? i.getDetectedAt() : i.getCreatedAt();
        return Duration.between(base, i.getStartedAt()).toMinutes();
    }

    @Transactional
    public Incident assign(UUID id, AssignIncidentRequest request){
        Incident incident = getById(id);

        if (incident.getStatus() == IncidentStatus.IN_PROGRESS || incident.getStatus() == IncidentStatus.CLOSED || incident.getStatus() == IncidentStatus.RESOLVED){
            String assignee = firstNotBlank(incident.getResponsible(), "другим пользователем");
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Этот инцидент уже взят в работу пользователем %s".formatted(assignee));
        }

        UUID userId = request != null && request.getUserId() != null ? request.getUserId() : DEFAULT_USER_ID;
        String userName = request != null ? firstNotBlank(request.getUserName(), DEFAULT_USER_NAME) : DEFAULT_USER_NAME;
        String role = request != null ? firstNotBlank(request.getRole(), DEFAULT_USER_ROLE) : DEFAULT_USER_ROLE;

        validateRoleForIncident(incident, CurrentActor.get().role());

        // Пользователь, вошедший в систему, уже есть в базе: его ФИО и должность не переписываем
        AppUser user = userRepository.findById(userId)
                .orElseGet(() -> userRepository.save(new AppUser(userId, userName, role)));

        LocalDateTime startedAt = LocalDateTime.now(clock);
        incident.setStatus(IncidentStatus.IN_PROGRESS);
        incident.setAssigneeId(user.getId());
        incident.setAssigneeRole(user.getRole());
        incident.setResponsible(user.getFullName());
        incident.setStartedAt(startedAt);

        LocalDateTime base = incident.getDetectedAt() != null
                ? incident.getDetectedAt()
                : incident.getCreatedAt();
        if (base != null) {
            incident.setReactionMinutes(Duration.between(base, startedAt).toMinutes());
        }

        Incident savedIncident = repository.save(incident);
        audit.record(AuditService.INCIDENT, savedIncident.getId(), "ASSIGNED",
                "Инцидент %s взят в работу: %s".formatted(savedIncident.getCode(), user.getFullName()));
        historyRepository.save(new IncidentHistory(
                savedIncident.getId(),
                "ASSIGNED",
                user.getId(),
                user.getFullName(),
                "Пользователь %s взял инцидент в работу".formatted(user.getFullName())
        ));

        return savedIncident;
    }

    private String firstNotBlank(String value, String defaultValue){
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    private String blankToNull(String value){
        return value != null && !value.isBlank() ? value : null;
    }

    private String buildIncidentTitle(Incident incident){
        List<String> locationParts = new ArrayList<>();

        addIfNotBlank(locationParts, incident.getWorkshop());
        addIfNotBlank(locationParts, incident.getHouse());
        addIfNotBlank(locationParts, incident.getZone());

        String title = incident.getType() != null
                ? incident.getType().getDisplayName()
                : IncidentType.OTHER.getDisplayName();

        if (!locationParts.isEmpty()){
            title += ": " + String.join(" / ", locationParts);
        }

        return title;
    }

    private void addIfNotBlank(List<String> values, String value){
        if (value != null && !value.isBlank()){
            values.add(value);
        }
    }

    public static String generateIncidentCode(){
        String datePart = LocalDateTime.now().format(INCIDENT_CODE_DATE_FORMAT);
        String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return "INC-" + datePart + "-" + randomPart;
    }

    private IncidentPriority mapNotificationPriority(Notification notification){
        return IncidentPriority.valueOf(notification.getPriority().name());
    }

    /**
     * Кто может взять инцидент в работу (S2-04): падёж и состояние стада — ветеринар, технолог,
     * руководитель, администратор; остальные инциденты — оператор, технолог, руководитель, администратор.
     */
    private void validateRoleForIncident(Incident incident, UserRole role){
        if (role == null) {
            return;
        }
        Set<UserRole> allowedRoles = isFlockHealth(incident)
                ? EnumSet.of(UserRole.VETERINARIAN, UserRole.TECHNOLOGIST, UserRole.MANAGER, UserRole.ADMIN)
                : EnumSet.of(UserRole.OPERATOR, UserRole.TECHNOLOGIST, UserRole.MANAGER, UserRole.ADMIN);

        if (!allowedRoles.contains(role)){
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Роль '%s' не может взять этот тип инцидента в работу".formatted(role));
        }
    }

    private boolean isFlockHealth(Incident incident){
        if (incident.getType() == IncidentType.FLOCK_HEALTH) {
            return true;
        }
        String payload = "%s %s".formatted(
                firstNotBlank(incident.getTitle(), ""),
                firstNotBlank(incident.getDescription(), "")
        ).toLowerCase(Locale.ROOT);
        return payload.contains("падеж") || payload.contains("падёж") || payload.contains("вет");
    }

    private static <T> T firstNonNull(T value, T defaultValue){
        return value != null ? value : defaultValue;
    }

}
