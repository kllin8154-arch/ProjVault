package com.projvault.security;

import com.projvault.common.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public ApiResponse<RbacUserDTO> login(@Valid @RequestBody AuthRequest request,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.getUsername(), request.getPassword());
        Cookie cookie = new Cookie(AuthService.COOKIE_NAME, result.token());
        cookie.setHttpOnly(true); cookie.setSecure(httpRequest.isSecure()); cookie.setPath("/"); cookie.setMaxAge(12 * 3600);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
        return ApiResponse.ok(RbacUserDTO.from(result.user()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request);
        Cookie cookie = new Cookie(AuthService.COOKIE_NAME, "");
        cookie.setHttpOnly(true); cookie.setPath("/"); cookie.setMaxAge(0); cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<RbacUserDTO> me(HttpServletRequest request) {
        return ApiResponse.ok(RbacUserDTO.from(authService.currentUser(request)
                .orElseThrow(() -> new com.projvault.common.BusinessException(401, "未登录"))));
    }
}
