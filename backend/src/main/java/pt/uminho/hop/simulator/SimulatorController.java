package pt.uminho.hop.simulator;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pt.uminho.hop.audit.AuditTrail;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponte de controlo do simulador (extensão E11). O simulador faz POST /status
 * a cada ~5s com o estado real; a resposta traz os cenários que o utilizador
 * pediu na UI e ainda não foram aplicados. Estado em memória — é uma
 * ferramenta de demonstração, não faz parte do domínio da plataforma.
 */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    public static final Set<String> SCENARIOS = Set.of("normal", "error-spike", "latency", "silence");
    private static final Duration CONNECTED_WINDOW = Duration.ofSeconds(15);

    public record ProfileStatus(String profile, String serviceName, UUID serviceId,
                                String scenario, Integer intervalSeconds) {}
    public record StatusRequest(List<ProfileStatus> profiles) {}
    public record ScenariosResponse(Map<String, String> scenarios) {}
    public record ProfileView(String profile, String serviceName, UUID serviceId,
                              String scenario, Integer intervalSeconds, String pendingScenario) {}
    public record SimulatorView(boolean connected, OffsetDateTime lastSeenAt, List<ProfileView> profiles) {}

    private final AuditTrail audit;
    private volatile OffsetDateTime lastSeenAt;
    private volatile List<ProfileStatus> reported = List.of();
    private final Map<String, String> pending = new ConcurrentHashMap<>();

    public SimulatorController(AuditTrail audit) {
        this.audit = audit;
    }

    /** Chamado pelo simulador: reporta o estado e recebe alterações pendentes. */
    @PostMapping("/status")
    public ScenariosResponse status(@RequestBody StatusRequest request) {
        lastSeenAt = OffsetDateTime.now();
        reported = request.profiles() == null ? List.of() : List.copyOf(request.profiles());
        // alterações já aplicadas pelo simulador deixam de estar pendentes
        for (ProfileStatus profile : reported) {
            String wanted = pending.get(profile.profile());
            if (wanted != null && wanted.equals(profile.scenario())) {
                pending.remove(profile.profile());
            }
        }
        return new ScenariosResponse(Map.copyOf(pending));
    }

    /** Estado para a UI. */
    @GetMapping
    public SimulatorView view() {
        boolean connected = lastSeenAt != null
                && lastSeenAt.isAfter(OffsetDateTime.now().minus(CONNECTED_WINDOW));
        List<ProfileView> profiles = reported.stream()
                .map(p -> new ProfileView(p.profile(), p.serviceName(), p.serviceId(),
                        p.scenario(), p.intervalSeconds(), pending.get(p.profile())))
                .toList();
        return new SimulatorView(connected, lastSeenAt, profiles);
    }

    /** UI pede a mudança de cenário de um perfil; o simulador aplica no próximo sync. */
    @PutMapping("/scenarios/{profile}")
    public SimulatorView setScenario(@PathVariable String profile,
                                     @RequestBody Map<String, String> body) {
        String scenario = body.get("scenario");
        if (scenario == null || !SCENARIOS.contains(scenario)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cenário inválido — usa um de: " + String.join(", ", SCENARIOS));
        }
        boolean known = reported.stream().anyMatch(p -> p.profile().equals(profile));
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Perfil desconhecido — o simulador ainda não reportou '" + profile + "'");
        }
        pending.put(profile, scenario);
        audit.user("SIMULATOR_SCENARIO_CHANGED", "SIMULATOR", null,
                Map.of("profile", profile, "scenario", scenario));
        return view();
    }
}
