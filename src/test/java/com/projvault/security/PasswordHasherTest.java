package com.projvault.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {
    @Test
    void hashesWithSaltAndVerifiesWithoutPlaintextStorage() {
        PasswordHasher hasher = new PasswordHasher();
        RbacUser user = new RbacUser();
        String salt = hasher.newSalt();
        user.setPasswordSalt(salt);
        user.setPasswordHash(hasher.hash("correct-password", salt));

        assertThat(user.getPasswordHash()).doesNotContain("correct-password");
        assertThat(hasher.matches("correct-password", user)).isTrue();
        assertThat(hasher.matches("wrong-password", user)).isFalse();
    }
}
