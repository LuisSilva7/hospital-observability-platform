package pt.uminho.hop.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, Object> health() {
        boolean dbUp;
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbUp = true;
        } catch (Exception e) {
            dbUp = false;
        }
        return Map.of(
                "status", dbUp ? "UP" : "DEGRADED",
                "database", dbUp ? "UP" : "DOWN",
                "timestamp", OffsetDateTime.now().toString()
        );
    }
}
