package com.projvault.ai;

import com.projvault.settings.UserAiSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAwareRagAnswerProviderTest {
    private final RagAnswerModelProvider system = mock(RagAnswerModelProvider.class);
    private final UserAiSettingService settings = mock(UserAiSettingService.class);
    private final UserConversationClient client = mock(UserConversationClient.class);
    private final UserAwareRagAnswerProvider provider = new UserAwareRagAnswerProvider(system, settings, client);

    @AfterEach
    void clearContext() {
        AiCallerContext.clear();
    }

    @Test
    void nonAdminWithoutPersonalConfigNeverFallsBackToSystemProvider() {
        AiCallerContext.set(12L, false);
        when(settings.resolve(12L)).thenReturn(Optional.empty());

        RagAnswer answer = provider.answer("问题", List.of("证据"));

        assertThat(answer.answer()).isEqualTo(UserAwareRagAnswerProvider.CONFIG_REQUIRED_MESSAGE);
        verify(system, never()).answer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonAdminUsesOnlyOwnDecryptedConfiguration() {
        AiCallerContext.set(12L, false);
        ConversationAiConfig config = new ConversationAiConfig(
                "https://api.example.com/v1", "secret", "model", 60);
        RagAnswer expected = new RagAnswer("个人回答", List.of(), true);
        when(settings.resolve(12L)).thenReturn(Optional.of(config));
        when(client.answer(config, "问题", List.of("证据"))).thenReturn(expected);

        assertThat(provider.answer("问题", List.of("证据"))).isSameAs(expected);
        verify(system, never()).answer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminContinuesToUseSystemProvider() {
        AiCallerContext.set(1L, true);
        RagAnswer expected = new RagAnswer("系统回答", List.of(), true);
        when(system.answer("问题", List.of("证据"))).thenReturn(expected);

        assertThat(provider.answer("问题", List.of("证据"))).isSameAs(expected);
        verify(settings, never()).resolve(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolvedConfigurationNeverPrintsApiKey() {
        ConversationAiConfig config = new ConversationAiConfig(
                "https://api.example.com/v1", "sk-sensitive", "model", 60);

        assertThat(config.toString()).contains("apiKey=***").doesNotContain("sk-sensitive");
    }
}
