package com.projvault.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限注解。所有 Controller 写操作必须标注（决策 D3）。
 * 权限码规范：pkc:资源:动作，如 pkc:scan:start、pkc:config:confirm。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /** 权限码，如 pkc:project:manage */
    String value();
}
