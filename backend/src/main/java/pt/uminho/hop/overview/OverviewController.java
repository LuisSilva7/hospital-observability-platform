package pt.uminho.hop.overview;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.uminho.hop.services.ServiceManager;
import pt.uminho.hop.services.domain.MonitoredService;
import pt.uminho.hop.services.repository.MonitoredServiceRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private final MonitoredServiceRepository services;
    private final pt.uminho.hop.alerts.repository.AlertRepository alerts;

    public OverviewController(MonitoredServiceRepository services,
                              pt.uminho.hop.alerts.repository.AlertRepository alerts) {
        this.services = services;
        this.alerts = alerts;
    }

    public record ServiceSummary(
            UUID id, String name, String criticality, String status,
            OffsetDateTime lastSeenAt, boolean active) {}

    @GetMapping
    public Map<String, Object> overview() {
        OffsetDateTime now = OffsetDateTime.now();
        List<MonitoredService> all = services.findAll();

        List<ServiceSummary> summaries = all.stream()
                .map(s -> new ServiceSummary(
                        s.getId(), s.getName(), s.getCriticality().name(),
                        ServiceManager.deriveStatus(s, now), s.getLastSeenAt(), s.isActive()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String status : List.of("HEALTHY", "SILENT", "UNKNOWN", "INACTIVE")) {
            byStatus.put(status, summaries.stream().filter(s -> s.status().equals(status)).count());
        }

        return Map.of(
                "totalServices", summaries.size(),
                "byStatus", byStatus,
                "services", summaries,
                "activeAlerts", alerts.countByStatusIn(List.of(
                        pt.uminho.hop.alerts.domain.Alert.Status.OPEN,
                        pt.uminho.hop.alerts.domain.Alert.Status.ACKNOWLEDGED))
        );
    }
}
