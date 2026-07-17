package pt.uminho.hop.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting da ingestão por serviço (token bucket em memória).
 * Capacidade = limite por minuto (absorve rajadas); reposição contínua.
 * 0 desativa. Estado é local ao processo — suficiente para o protótipo.
 */
@Component
public class IngestRateLimiter {

    private final int perMinute;
    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    public IngestRateLimiter(@Value("${hop.ingest.rate-limit-per-minute:300}") int perMinute) {
        this.perMinute = perMinute;
    }

    /** Devolve 0 se o pedido é permitido; caso contrário, segundos a esperar. */
    public long tryAcquire(UUID serviceId) {
        if (perMinute <= 0) {
            return 0;
        }
        return buckets.computeIfAbsent(serviceId, id -> new Bucket(perMinute)).tryConsume();
    }

    static final class Bucket {
        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastRefill = System.nanoTime();

        Bucket(int perMinute) {
            this.capacity = perMinute;
            this.tokens = perMinute;
            this.refillPerNano = perMinute / 60e9;
        }

        synchronized long tryConsume() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return (long) Math.ceil((1 - tokens) / (refillPerNano * 1e9));
        }
    }
}
