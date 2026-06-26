package com.projvault.settings;

import com.projvault.ai.ConversationAiConfig;
import com.projvault.ai.UserConversationClient;
import com.projvault.common.ApiResponse;
import com.projvault.common.BusinessException;
import com.projvault.security.ProjectAccessService;
import com.projvault.security.RbacUser;
import com.projvault.security.RequirePerm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/pkc/me/ai-settings")
public class UserAiSettingController {
    private final ProjectAccessService accessService;
    private final UserAiSettingService settingService;
    private final UserConversationClient client;

    public UserAiSettingController(ProjectAccessService accessService,
                                   UserAiSettingService settingService,
                                   UserConversationClient client) {
        this.accessService = accessService;
        this.settingService = settingService;
        this.client = client;
    }

    @GetMapping
    @RequirePerm("pkc:ai:configure")
    public ApiResponse<UserAiSettingDTO> get(HttpServletRequest request) {
        RbacUser user = accessService.currentUser(request);
        settingService.requirePersonalAccount(user);
        return ApiResponse.ok(settingService.get(user.getId()));
    }

    @PutMapping
    @RequirePerm("pkc:ai:configure")
    public ApiResponse<UserAiSettingDTO> save(HttpServletRequest request,
                                              @Valid @RequestBody UserAiSettingRequest body) {
        return ApiResponse.ok(settingService.save(accessService.currentUser(request), body));
    }

    @DeleteMapping
    @RequirePerm("pkc:ai:configure")
    public ApiResponse<Void> delete(HttpServletRequest request) {
        settingService.delete(accessService.currentUser(request));
        return ApiResponse.ok();
    }

    @PostMapping("/test")
    @RequirePerm("pkc:ai:configure")
    public ApiResponse<Map<String, Object>> test(HttpServletRequest request) {
        RbacUser user = accessService.currentUser(request);
        settingService.requirePersonalAccount(user);
        ConversationAiConfig config = settingService.resolve(user.getId())
                .orElseThrow(() -> new BusinessException(422, "请先保存个人对话模型配置"));
        String response = client.test(config);
        return ApiResponse.ok(Map.of("ok", true, "message", "连接正常：" + response.strip()));
    }
}
