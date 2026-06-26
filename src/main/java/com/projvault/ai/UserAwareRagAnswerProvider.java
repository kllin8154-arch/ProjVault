package com.projvault.ai;

import com.projvault.settings.UserAiSettingService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class UserAwareRagAnswerProvider implements RagAnswerModelProvider {
    public static final String CONFIG_REQUIRED_MESSAGE =
            "当前账号尚未配置独立的对话模型 API。请前往系统设置完成个人 AI 配置；系统 API 不会代替当前账号调用。";

    private final RagAnswerModelProvider systemProvider;
    private final UserAiSettingService settings;
    private final UserConversationClient client;

    public UserAwareRagAnswerProvider(
            @Qualifier("systemRagAnswerProvider") RagAnswerModelProvider systemProvider,
            UserAiSettingService settings,
            UserConversationClient client) {
        this.systemProvider = systemProvider;
        this.settings = settings;
        this.client = client;
    }

    @Override
    public RagAnswer answer(String question, List<String> contextChunks) {
        var personalCaller = AiCallerContext.current().filter(caller -> !caller.admin());
        if (personalCaller.isEmpty()) {
            return systemProvider.answer(question, contextChunks);
        }
        try {
            return settings.resolve(personalCaller.get().userId())
                    .map(config -> client.answer(config, question, contextChunks))
                    .orElseGet(() -> new RagAnswer(CONFIG_REQUIRED_MESSAGE, List.of(), false));
        } catch (Exception e) {
            return new RagAnswer("当前账号的个人 AI 配置无法解密，请清除后重新配置。", List.of(), false);
        }
    }
}
