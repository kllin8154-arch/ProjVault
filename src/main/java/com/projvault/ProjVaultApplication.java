package com.projvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ProjVault - 项目现场资料管理平台
 * 模块一：项目知识中心（Project Knowledge Center, PKC）
 */
@SpringBootApplication
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class ProjVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjVaultApplication.class, args);
    }
}
