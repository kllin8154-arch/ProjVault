package com.projvault.settings;

import com.projvault.security.RbacRole;
import com.projvault.security.RbacUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAiSettingServiceTest {
    private final UserAiSettingRepository repository = mock(UserAiSettingRepository.class);
    private final SecretCryptoService crypto = mock(SecretCryptoService.class);
    private final UserAiSettingService service = new UserAiSettingService(repository, crypto);

    @Test
    void savesOnlyCiphertextAndNeverReturnsApiKey() {
        RbacUser user = user(9L, "PROJECT_MANAGER");
        UserAiSettingRequest request = new UserAiSettingRequest();
        request.setBaseUrl("https://api.deepseek.com/v1/");
        request.setModel("deepseek-chat");
        request.setApiKey("sk-private");
        request.setTimeoutSeconds(60);
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(crypto.encrypt(9L, "sk-private")).thenReturn("v1:ciphertext");
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserAiSettingDTO result = service.save(user, request);

        ArgumentCaptor<UserAiSetting> captor = ArgumentCaptor.forClass(UserAiSetting.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedApiKey()).isEqualTo("v1:ciphertext");
        assertThat(captor.getValue().getEncryptedApiKey()).doesNotContain("sk-private");
        assertThat(captor.getValue().getBaseUrl()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(result.apiKeySet()).isTrue();
        assertThat(result.toString()).doesNotContain("sk-private").doesNotContain("ciphertext");
    }

    private RbacUser user(Long id, String roleCode) {
        RbacRole role = new RbacRole();
        role.setCode(roleCode);
        RbacUser user = new RbacUser();
        user.setId(id);
        user.setRoles(new LinkedHashSet<>(Set.of(role)));
        return user;
    }
}
