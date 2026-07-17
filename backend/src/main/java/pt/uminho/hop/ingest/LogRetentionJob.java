package pt.uminho.hop.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.uminho.hop.audit.AuditTrail;
import pt.uminho.hop.events.SseHub;
import pt.uminho.hop.ingest.repository.LogEventRepository;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Retenção de logs (extensão E4): apaga logs com mais de N dias, exceto os
 * associados a alertas (mantidos como evidência da timeline). N vem de
 * LOG_RETENTION_DAYS; 0 desativa a limpeza.
 */
@Component
public class LogRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionJob.class);

    private final LogEventRepository logEvents;
    private final AuditTrail audit;
    private final SseHub sse;
    private final int retentionDays;

    public LogRetentionJob(LogEventRepository logEvents,
                           AuditTrail audit,
                           SseHub sse,
                           @Value("${hop.retention.log-days:30}") int retentionDays) {
        this.logEvents = logEvents;
        this.audit = audit;
        this.sse = sse;
        this.retentionDays = retentionDays;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    @Transactional
    public void purge() {
        if (retentionDays <= 0) {
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        int deleted = logEvents.deleteUnlinkedOlderThan(cutoff);
        if (deleted > 0) {
            audit.system("LOGS_PURGED", "LOG", null,
                    Map.of("deleted", deleted, "cutoff", cutoff.toString(),
                            "retentionDays", retentionDays));
            sse.publish("logs");
            log.info("Retenção: apagados {} logs anteriores a {}", deleted, cutoff);
        }
    }
}
