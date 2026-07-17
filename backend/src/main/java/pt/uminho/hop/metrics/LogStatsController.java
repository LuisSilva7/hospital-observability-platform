package pt.uminho.hop.metrics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agregações para os gráficos do dashboard (extensão E10): volume por hora,
 * mensagens de erro mais frequentes e distribuição por serviço.
 * Tudo calculado com GROUP BY sobre log_event — sem estado próprio.
 */
@RestController
@RequestMapping("/api/stats")
public class LogStatsController {

    private final JdbcTemplate jdbc;
    private final MonitoredServiceRepository services;

    public LogStatsController(JdbcTemplate jdbc, MonitoredServiceRepository services) {
        this.jdbc = jdbc;
        this.services = services;
    }

    public record HourBucket(OffsetDateTime hour, long total, long errors, long warns) {}
    public record TopError(String message, long count) {}
    public record ServiceCount(UUID serviceId, String serviceName, long total, long errors) {}
    public record LogStatsResponse(
            int hours, List<HourBucket> volumePerHour,
            List<TopError> topErrors, List<ServiceCount> byService) {}

    @GetMapping("/logs")
    public LogStatsResponse logs(@RequestParam(defaultValue = "24") int hours) {
        int window = Math.min(Math.max(hours, 1), 168);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime from = now.truncatedTo(ChronoUnit.HOURS).minusHours(window - 1L);

        // volume por hora, com horas vazias preenchidas a zero.
        // Chaveado por Instant (absoluto) para alinhar independentemente do fuso
        // do JVM/Postgres — o date_trunc devolve instantes, não horas locais.
        Map<java.time.Instant, HourBucket> byHour = new LinkedHashMap<>();
        jdbc.query("""
                select date_trunc('hour', received_at) as hour,
                       count(*) as total,
                       count(*) filter (where level in ('ERROR','FATAL')) as errors,
                       count(*) filter (where level = 'WARN') as warns
                from log_event
                where received_at >= ?
                group by 1
                """, rs -> {
            OffsetDateTime hour = rs.getObject("hour", OffsetDateTime.class);
            byHour.put(hour.toInstant(),
                    new HourBucket(hour, rs.getLong("total"), rs.getLong("errors"), rs.getLong("warns")));
        }, from);
        List<HourBucket> volume = new ArrayList<>();
        for (int i = 0; i < window; i++) {
            OffsetDateTime hour = from.plusHours(i);
            HourBucket bucket = byHour.get(hour.toInstant());
            volume.add(bucket != null ? bucket : new HourBucket(hour, 0, 0, 0));
        }

        List<TopError> topErrors = jdbc.query("""
                select message, count(*) as count
                from log_event
                where received_at >= ? and level in ('ERROR','FATAL') and message is not null
                group by message
                order by count desc, message
                limit 6
                """, (rs, i) -> new TopError(rs.getString("message"), rs.getLong("count")), from);

        Map<UUID, String> names = services.findAll().stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));
        List<ServiceCount> byService = jdbc.query("""
                select service_id,
                       count(*) as total,
                       count(*) filter (where level in ('ERROR','FATAL')) as errors
                from log_event
                where received_at >= ?
                group by service_id
                order by total desc
                """, (rs, i) -> {
            UUID serviceId = rs.getObject("service_id", UUID.class);
            return new ServiceCount(serviceId,
                    names.getOrDefault(serviceId, "(removido)"),
                    rs.getLong("total"), rs.getLong("errors"));
        }, from);

        return new LogStatsResponse(window, volume, topErrors, byService);
    }
}
