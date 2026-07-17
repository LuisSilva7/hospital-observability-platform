package pt.uminho.hop.events;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Difusão de eventos Server-Sent Events para o frontend (substitui o polling).
 * Os eventos são só sinais "algo mudou neste tópico" — o cliente volta a
 * pedir os dados via REST; nunca transportam dados de domínio.
 * Tópicos: logs, alerts, executions, analyses, services, rules.
 *
 * A entrega é feita num executor próprio (nunca bloqueia o thread de ingestão
 * nem o scheduler) e, quando há uma transação ativa, só depois do commit — para
 * o cliente nunca refazer o fetch antes de os dados estarem visíveis nem receber
 * sinais de dados que a transação acabou por reverter.
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ExecutorService dispatcher =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sse-dispatch");
                t.setDaemon(true);
                return t;
            });

    public SseEmitter subscribe() {
        // sem timeout do lado do servidor; o heartbeat mantém a ligação viva
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** Publica um sinal de mudança; após o commit se houver transação ativa. */
    public void publish(String topic) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(topic);
                }
            });
        } else {
            dispatch(topic);
        }
    }

    private void dispatch(String topic) {
        String payload = Instant.now().toString();
        dispatcher.submit(() -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name(topic).data(payload));
                } catch (Exception e) {
                    // cliente desligou-se sem aviso — remover silenciosamente
                    emitters.remove(emitter);
                }
            }
        });
    }

    public int subscriberCount() {
        return emitters.size();
    }

    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        if (!emitters.isEmpty()) {
            dispatch("ping");
        }
    }

    @PreDestroy
    void shutdown() {
        dispatcher.shutdownNow();
    }
}
