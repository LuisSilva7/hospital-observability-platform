package pt.uminho.hop.rules;

import pt.uminho.hop.rules.domain.MonitorRule;

import java.util.UUID;

/**
 * Ponto de extensão chamado quando uma regra dispara.
 * No Módulo 5 apenas persiste a avaliação; o Módulo 6 cria alertas
 * e o Módulo 7 executa automações a partir daqui.
 */
public interface RuleTriggerHandler {
    void onRuleTriggered(MonitorRule rule, UUID logEventId, String details);
}
