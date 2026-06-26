package com.projvault.ai;

import java.util.Optional;

public final class AiCallerContext {
    private static final ThreadLocal<Caller> CURRENT = new ThreadLocal<>();

    private AiCallerContext() {
    }

    public static void set(Long userId, boolean admin) {
        CURRENT.set(new Caller(userId, admin));
    }

    public static Optional<Caller> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Caller(Long userId, boolean admin) {
    }
}
