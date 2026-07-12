package pt.uminho.hop.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Gera API keys do formato "hop_<40 chars aleatórios>".
 * Só o hash SHA-256 é persistido; o prefixo (primeiros 12 chars) fica
 * em claro para identificação na UI sem expor a chave.
 */
public final class ApiKeyGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int KEY_LENGTH = 40;
    public static final int PREFIX_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyGenerator() {}

    public record GeneratedKey(String plainKey, String prefix, String hash) {}

    public static GeneratedKey generate() {
        StringBuilder sb = new StringBuilder("hop_");
        for (int i = 0; i < KEY_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        String key = sb.toString();
        return new GeneratedKey(key, key.substring(0, PREFIX_LENGTH), sha256(key));
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
