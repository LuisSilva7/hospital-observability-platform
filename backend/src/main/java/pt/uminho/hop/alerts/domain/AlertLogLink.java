package pt.uminho.hop.alerts.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "alert_log_link")
@IdClass(AlertLogLink.Key.class)
public class AlertLogLink {

    @Id
    @Column(name = "alert_id")
    private UUID alertId;

    @Id
    @Column(name = "log_event_id")
    private UUID logEventId;

    public AlertLogLink() {}

    public AlertLogLink(UUID alertId, UUID logEventId) {
        this.alertId = alertId;
        this.logEventId = logEventId;
    }

    public UUID getAlertId() { return alertId; }
    public UUID getLogEventId() { return logEventId; }

    public static class Key implements Serializable {
        private UUID alertId;
        private UUID logEventId;

        public Key() {}

        public Key(UUID alertId, UUID logEventId) {
            this.alertId = alertId;
            this.logEventId = logEventId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key key)) return false;
            return Objects.equals(alertId, key.alertId) && Objects.equals(logEventId, key.logEventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(alertId, logEventId);
        }
    }
}
