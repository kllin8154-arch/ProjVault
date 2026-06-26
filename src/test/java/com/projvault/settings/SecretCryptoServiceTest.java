package com.projvault.settings;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCryptoServiceTest {

    @Test
    void encryptsWithRandomIvAndBindsCiphertextToUser() {
        String masterKey = Base64.getEncoder().encodeToString(new byte[32]);
        SecretCryptoService crypto = new SecretCryptoService(masterKey, "unused");
        crypto.initialize();

        String first = crypto.encrypt(7L, "sk-user-secret");
        String second = crypto.encrypt(7L, "sk-user-secret");

        assertThat(first).startsWith("v1:").doesNotContain("sk-user-secret").isNotEqualTo(second);
        assertThat(crypto.decrypt(7L, first)).isEqualTo("sk-user-secret");
        assertThatThrownBy(() -> crypto.decrypt(8L, first))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("归属校验失败");
    }
}
