package com.projvault.security;

import com.projvault.common.BusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    public static final String COOKIE_NAME = "PV_SESSION";
    private final RbacUserRepository users;
    private final PasswordHasher hasher;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    @Value("${projvault.security.session-hours:12}") private long sessionHours;

    public AuthService(RbacUserRepository users, PasswordHasher hasher) {
        this.users = users; this.hasher = hasher;
    }

    public LoginResult login(String username, String password) {
        RbacUser user = users.findByUsername(username == null ? "" : username.strip())
                .filter(RbacUser::isEnabled)
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!hasher.matches(password == null ? "" : password, user)) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(user.getId(), Instant.now().plusSeconds(sessionHours * 3600)));
        return new LoginResult(token, user);
    }

    public Optional<RbacUser> currentUser(HttpServletRequest request) {
        String token = token(request);
        Session session = token == null ? null : sessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            if (token != null) sessions.remove(token);
            return Optional.empty();
        }
        return users.findById(session.userId()).filter(RbacUser::isEnabled);
    }

    public void logout(HttpServletRequest request) {
        String token = token(request); if (token != null) sessions.remove(token);
    }

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private record Session(Long userId, Instant expiresAt) {}
    public record LoginResult(String token, RbacUser user) {}
}
