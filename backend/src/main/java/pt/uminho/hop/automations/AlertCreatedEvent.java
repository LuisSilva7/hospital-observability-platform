package pt.uminho.hop.automations;

import java.util.UUID;

/** Publicado pelo AlertManager quando um alerta novo é criado. */
public record AlertCreatedEvent(UUID alertId, UUID ruleId) {}
