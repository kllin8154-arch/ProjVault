package com.projvault.settings;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class SecretCryptoService {
    private static final Logger log = LoggerFactory.getLogger(SecretCryptoService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_LENGTH = 12;
    private final String configuredMasterKey;
    private final Path keyFile;
    private SecretKey masterKey;

    public SecretCryptoService(
            @Value("${projvault.security.secrets.master-key:}") String configuredMasterKey,
            @Value("${projvault.security.secrets.key-file:./data/.projvault-secrets.key}") String keyFile) {
        this.configuredMasterKey = configuredMasterKey == null ? "" : configuredMasterKey.strip();
        this.keyFile = Paths.get(keyFile).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() {
        try {
            byte[] keyBytes = configuredMasterKey.isBlank()
                    ? loadOrCreateLocalKey()
                    : Base64.getDecoder().decode(configuredMasterKey);
            if (keyBytes.length != 32) {
                throw new IllegalStateException("PROJVAULT_SECRETS_MASTER_KEY 必须是 Base64 编码的 32 字节密钥");
            }
            masterKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化用户密钥加密服务", e);
        }
    }

    public String encrypt(Long userId, String plaintext) {
        requireInitialized();
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(userId));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return "v1:" + Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    public String decrypt(Long userId, String ciphertext) {
        requireInitialized();
        try {
            if (ciphertext == null || !ciphertext.startsWith("v1:")) {
                throw new IllegalArgumentException("不支持的密文版本");
            }
            byte[] packed = Base64.getDecoder().decode(ciphertext.substring(3));
            if (packed.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文损坏");
            }
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, IV_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(packed, IV_LENGTH, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(userId));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败或归属校验失败", e);
        }
    }

    private byte[] loadOrCreateLocalKey() throws Exception {
        Files.createDirectories(keyFile.getParent());
        if (!Files.exists(keyFile)) {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            String encoded = Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
            try {
                Files.writeString(keyFile, encoded, StandardCharsets.US_ASCII,
                        java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
                restrictKeyFile();
                log.warn("已生成本机用户密钥主密钥文件 {}。生产环境建议改用 PROJVAULT_SECRETS_MASTER_KEY。", keyFile);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // 并发启动时由另一实例创建，随后读取。
            }
        }
        return Base64.getDecoder().decode(Files.readString(keyFile, StandardCharsets.US_ASCII).strip());
    }

    private void restrictKeyFile() {
        try {
            Files.setPosixFilePermissions(keyFile, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            return;
        } catch (UnsupportedOperationException | java.io.IOException ignored) {
            // Windows 使用 ACL。
        }
        try {
            AclFileAttributeView view = Files.getFileAttributeView(keyFile, AclFileAttributeView.class);
            if (view != null) {
                AclEntry ownerOnly = AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(Files.getOwner(keyFile))
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                        .build();
                view.setAcl(List.of(ownerOnly));
            }
            DosFileAttributeView dos = Files.getFileAttributeView(keyFile, DosFileAttributeView.class);
            if (dos != null) dos.setHidden(true);
        } catch (Exception e) {
            log.warn("无法收紧本地主密钥文件权限，请检查 {} 的操作系统 ACL。", keyFile);
        }
    }

    private byte[] aad(Long userId) {
        return ("projvault:user-ai:" + userId).getBytes(StandardCharsets.UTF_8);
    }

    private void requireInitialized() {
        if (masterKey == null) throw new IllegalStateException("用户密钥加密服务尚未初始化");
    }
}
