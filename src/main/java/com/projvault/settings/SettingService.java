package com.projvault.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设置读写服务。get 带默认值（DB 无值时回退 application.yml 默认）。
 */
@Service
public class SettingService {

    private final SettingRepository repo;

    public SettingService(SettingRepository repo) {
        this.repo = repo;
    }

    public String get(String key, String def) {
        return repo.findById(key)
                .map(Setting::getSettingValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(def);
    }

    @Transactional
    public void set(String key, String value) {
        Setting s = repo.findById(key).orElseGet(() -> {
            Setting n = new Setting();
            n.setSettingKey(key);
            return n;
        });
        s.setSettingValue(value);
        repo.save(s);
    }
}
