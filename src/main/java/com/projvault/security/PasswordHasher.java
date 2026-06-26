package com.projvault.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordHasher {
    private static final SecureRandom RANDOM = new SecureRandom();

    public String newSalt() {
        byte[] salt = new byte[18];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String hash(String password, String encodedSalt) {
        try {
            byte[] salt = Base64.getDecoder().decode(encodedSalt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120_000, 256);
            byte[] value = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.getEncoder().encodeToString(value);
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }

    public boolean matches(String password, RbacUser user) {
        return MessageDigest.isEqual(hash(password, user.getPasswordSalt()).getBytes(),
                user.getPasswordHash().getBytes());
    }
}
