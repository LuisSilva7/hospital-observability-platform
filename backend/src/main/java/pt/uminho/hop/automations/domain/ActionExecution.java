package pt.uminho.hop.automations.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "action_execution")
public class ActionExecution {

    public enum Status { SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "action_id")
    private UUID actionId;

    /** Tipo da ação no momento da execução — preservado mesmo que a ação mude depois. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private AutomationAction.Type actionType = AutomationAction.Type.WEBHOOK;

    @Column(name = "alert_id")
    private UUID alertId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts = 1;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body")
    private String responseBody;

    private String error;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private OffsetDateTime executedAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public UUID getActionId() { return actionId; }
    public void setActionId(UUID actionId) { this.actionId = actionId; }
    public AutomationAction.Type getActionType() { return actionType; }
    public void setActionType(AutomationAction.Type actionType) { this.actionType = actionType; }
    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Integer getResponseCode() { return responseCode; }
    public void setResponseCode(Integer responseCode) { this.responseCode = responseCode; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
}
