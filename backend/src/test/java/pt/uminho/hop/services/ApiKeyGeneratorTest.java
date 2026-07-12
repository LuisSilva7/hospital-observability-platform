package pt.uminho.hop.services;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyGeneratorTest {

    @Test
    void generatesKeyWithExpectedFormat() {
        var key = ApiKeyGenerator.generate();
        assertThat(key.plainKey()).startsWith("hop_").hasSize(44);
        assertThat(key.prefix()).isEqualTo(key.plainKey().substring(0, 12));
        assertThat(key.hash()).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void hashMatchesSha256OfPlainKey() {
        var key = ApiKeyGenerator.generate();
        assertThat(ApiKeyGenerator.sha256(key.plainKey())).isEqualTo(key.hash());
    }

    @Test
    void keysAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(seen.add(ApiKeyGenerator.generate().plainKey())).isTrue();
        }
    }
}
