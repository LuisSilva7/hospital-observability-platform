package pt.uminho.hop.ingest.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "log_event")
public class LogEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    @Column(name = "event_timestamp")
    private OffsetDateTime eventTimestamp;

    @Column(length = 20)
    private String level;

    private String message;

    @Column(name = "event_type", length = 120)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    public UUID getId() { return id; }
    public UUID getServiceId() { return serviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public OffsetDateTime getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(OffsetDateTime eventTimestamp) { this.eventTimestamp = eventTimestamp; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
