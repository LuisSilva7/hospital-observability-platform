package pt.uminho.hop.alerts.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_event")
public class AlertEvent {

    public enum Type { CREATED, TRIGGER_REPEATED, ACKNOWLEDGED, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public static AlertEvent of(UUID alertId, Type type, String description) {
        AlertEvent event = new AlertEvent();
        event.alertId = alertId;
        event.type = type;
        event.description = description;
        return event;
    }

    public UUID getId() { return id; }
    public UUID getAlertId() { return alertId; }
    public Type getType() { return type; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
