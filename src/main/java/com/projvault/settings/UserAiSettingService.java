package com.projvault.settings;

import com.projvault.ai.ConversationAiConfig;
import com.projvault.common.BusinessException;
import com.projvault.security.RbacUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserAiSettingService {
    private final UserAiSettingRepository repository;
    private final SecretCryptoService cryptoService;

    public UserAiSettingService(UserAiSettingRepository repository, SecretCryptoService cryptoService) {
        this.repository = repository;
        this.cryptoService = cryptoService;
    }

    public UserAiSettingDTO get(Long userId) {
        return repository.findById(userId)
                .map(this::toDto)
                .orElse(new UserAiSettingDTO("openai-compatible", "", "", 60, false, null));
    }

    public Optional<ConversationAiConfig> resolve(Long userId) {
        return repository.findById(userId).map(setting -> new ConversationAiConfig(
                setting.getBaseUrl(),
                cryptoService.decrypt(userId, setting.getEncryptedApiKey()),
                setting.getModel(),
                setting.getTimeoutSeconds()));
    }

    @Transactional
    public UserAiSettingDTO save(RbacUser user, UserAiSettingRequest request) {
        requirePersonalAccount(user);
        String baseUrl = normalizeBaseUrl(request.getBaseUrl());
        String model = cleanModel(request.getModel());
        UserAiSetting setting = repository.findById(user.getId()).orElseGet(UserAiSetting::new);
        setting.setUserId(user.getId());
        setting.setBaseUrl(baseUrl);
        setting.setModel(model);
        setting.setTimeoutSeconds(request.getTimeoutSeconds());
        String apiKey = cleanApiKey(request.getApiKey());
        if (apiKey != null) {
            setting.setEncryptedApiKey(cryptoService.encrypt(user.getId(), apiKey));
        } else if (setting.getEncryptedApiKey() == null || setting.getEncryptedApiKey().isBlank()) {
            throw new BusinessException(422, "首次配置必须填写 API Key");
        }
        setting.setUpdatedAt(LocalDateTime.now());
        return toDto(repository.save(setting));
    }

    @Transactional
    public void delete(RbacUser user) {
        requirePersonalAccount(user);
        repository.deleteById(user.getId());
    }

    public void requirePersonalAccount(RbacUser user) {
        boolean admin = user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode()));
        if (admin) throw new BusinessException(400, "管理员使用系统对话模型配置");
    }

    private UserAiSettingDTO toDto(UserAiSetting setting) {
        return new UserAiSettingDTO("openai-compatible", setting.getBaseUrl(), setting.getModel(),
                setting.getTimeoutSeconds(), setting.getEncryptedApiKey() != null, setting.getUpdatedAt());
    }

    private String normalizeBaseUrl(String value) {
        try {
            String raw = value.strip();
            URI uri = URI.create(raw);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("https".equals(scheme) || "http".equals(scheme)) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
                throw new IllegalArgumentException();
            }
            if ("http".equals(scheme) && !isLoopback(uri.getHost())) {
                throw new BusinessException(422, "外部模型地址必须使用 HTTPS；HTTP 仅允许本机 Ollama");
            }
            return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(422, "Base URL 必须是有效的 HTTP(S) 地址，且不能包含账号、查询参数或片段");
        }
    }

    private boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
    }

    private String cleanModel(String value) {
        String clean = value.strip();
        if (clean.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(422, "模型名称不能包含控制字符");
        }
        return clean;
    }

    private String cleanApiKey(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.strip();
        if (clean.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(422, "API Key 格式非法");
        }
        return clean;
    }
}
