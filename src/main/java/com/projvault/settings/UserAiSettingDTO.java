package com.projvault.settings;

import java.time.LocalDateTime;

public record UserAiSettingDTO(
        String provider,
        String baseUrl,
        String model,
        int timeoutSeconds,
        boolean apiKeySet,
        LocalDateTime updatedAt) {
}
