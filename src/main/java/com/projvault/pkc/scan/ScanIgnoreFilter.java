package com.projvault.pkc.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫描忽略规则（借鉴 UA .understandignore 机制）。
 * 内置默认规则 + 项目根目录 .projvaultignore / .understandignore 追加规则。
 * 语法子集：以 / 结尾匹配目录名；*.ext 匹配扩展名；其余按文件名包含匹配；# 注释。
 */
public class ScanIgnoreFilter {

    private static final List<String> DEFAULT_PATTERNS = List.of(
            // 临时与系统文件
            "~$*", "*.tmp", "*.temp", ".DS_Store", "Thumbs.db", "*.log",
            // 构建产物与依赖
            "node_modules/", "target/", "dist/", "build/", "out/", ".git/", ".idea/",
            // 平台/工具自身产物与配置目录
            ".understand-anything/", ".projvault/", "data/", ".claude/",
            // AI 生成草稿在项目经理审查前不得反向进入知识库
            "AI交付物/待审查/",
            // 忽略规则文件自身（不作为资料扫描）
            ".understandignore", ".projvaultignore",
            // 实施报告目录（自扫描时避免元噪声）
            "z/");

    /** ProjVault 需要图片（原型图等），不照搬 .understandignore 中的图片排除规则 */
    private static final java.util.Set<String> WANTED_EXT_PATTERNS = java.util.Set.of(
            "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.svg", "*.tif", "*.tiff", "*.webp",
            "*.zip", "*.rar", "*.7z");

    private final List<String> patterns = new ArrayList<>(DEFAULT_PATTERNS);

    public ScanIgnoreFilter(Path projectRoot) {
        loadExtraRules(projectRoot.resolve(".projvaultignore"));
        loadExtraRules(projectRoot.resolve(".understandignore"));
    }

    private void loadExtraRules(Path ignoreFile) {
        if (!Files.isRegularFile(ignoreFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(ignoreFile)) {
                String rule = line.trim();
                if (rule.isEmpty() || rule.startsWith("#")) {
                    continue;
                }
                if (WANTED_EXT_PATTERNS.contains(rule.toLowerCase())) {
                    continue; // ProjVault 需要图片，跳过外部 ignore 的图片排除
                }
                patterns.add(rule);
            }
        } catch (IOException e) {
            // 忽略规则文件读取失败不阻断扫描
        }
    }

    /**
     * @param relPath 相对项目根目录的路径（使用 / 分隔）
     * @return true 表示应忽略
     */
    public boolean shouldIgnore(String relPath) {
        String fileName = relPath.contains("/")
                ? relPath.substring(relPath.lastIndexOf('/') + 1)
                : relPath;
        for (String p : patterns) {
            if (p.endsWith("/")) {
                String dir = p.substring(0, p.length() - 1);
                if (relPath.startsWith(dir + "/") || relPath.contains("/" + dir + "/")) {
                    return true;
                }
            } else if (p.startsWith("*.")) {
                if (fileName.toLowerCase().endsWith(p.substring(1).toLowerCase())) {
                    return true;
                }
            } else if (p.startsWith("~$")) {
                if (fileName.startsWith("~$")) {
                    return true;
                }
            } else if (fileName.equalsIgnoreCase(p) || fileName.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
