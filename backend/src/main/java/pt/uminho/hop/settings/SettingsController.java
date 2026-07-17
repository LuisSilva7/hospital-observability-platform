package pt.uminho.hop.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.uminho.hop.ai.llm.LlmProvider;

import java.util.Arrays;
import java.util.List;

/**
 * Estado das integrações (módulo 9). Só expõe se estão configuradas e com que
 * parâmetros não-sensíveis — segredos (LLM_API_KEY) vivem apenas em env vars
 * e nunca saem pela API.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final LlmProvider llm;
    private final pt.uminho.hop.automations.AutomationExecutor executor;
    private final List<String> redactedFields;
    private final String n8nBaseUrl;
    private final int logRetentionDays;
    private final String mailHost;
    private final String mailFrom;

    public SettingsController(LlmProvider llm,
                              pt.uminho.hop.automations.AutomationExecutor executor,
                              @Value("${hop.llm.redacted-fields:}") String redactedFieldsCsv,
                              @Value("${hop.n8n.webhook-base-url:}") String n8nBaseUrl,
                              @Value("${hop.retention.log-days:30}") int logRetentionDays,
                              @Value("${spring.mail.host:}") String mailHost,
                              @Value("${hop.mail.from:hop@localhost}") String mailFrom) {
        this.llm = llm;
        this.executor = executor;
        this.redactedFields = Arrays.stream(redactedFieldsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        this.n8nBaseUrl = n8nBaseUrl == null ? "" : n8nBaseUrl.trim();
        this.logRetentionDays = logRetentionDays;
        this.mailHost = mailHost == null ? "" : mailHost.trim();
        this.mailFrom = mailFrom;
    }

    public record LlmSettings(boolean configured, String provider, String model, List<String> redactedFields) {}
    public record N8nSettings(boolean configured, String webhookBaseUrl) {}
    public record RetentionSettings(boolean enabled, int logDays) {}
    public record EmailSettings(boolean configured, String host, String from) {}
    public record SettingsResponse(LlmSettings llm, N8nSettings n8n,
                                   RetentionSettings retention, EmailSettings email) {}

    @GetMapping
    public SettingsResponse get() {
        return new SettingsResponse(
                new LlmSettings(llm.isConfigured(), llm.name(), llm.model(), redactedFields),
                new N8nSettings(!n8nBaseUrl.isBlank(), n8nBaseUrl.isBlank() ? null : n8nBaseUrl),
                new RetentionSettings(logRetentionDays > 0, logRetentionDays),
                new EmailSettings(executor.isMailConfigured(),
                        mailHost.isBlank() ? null : mailHost, mailFrom));
    }
}
