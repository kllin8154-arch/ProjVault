package com.projvault.pkc.file;

/**
 * P2 单文件变更记录（内存传递，不持久化）。
 * 供后续 P3-P7 各阶段决定是否需要重新处理该文件。
 */
public record FileChangeRecord(
        FileAsset asset,
        ChangeType changeType,
        String oldRelPath   // 仅 RENAMED 时非 null
) {

    public static FileChangeRecord added(FileAsset asset) {
        return new FileChangeRecord(asset, ChangeType.ADDED, null);
    }

    public static FileChangeRecord modified(FileAsset asset) {
        return new FileChangeRecord(asset, ChangeType.MODIFIED, null);
    }

    public static FileChangeRecord renamed(FileAsset asset, String oldRelPath) {
        return new FileChangeRecord(asset, ChangeType.RENAMED, oldRelPath);
    }

    public static FileChangeRecord deleted(FileAsset asset) {
        return new FileChangeRecord(asset, ChangeType.DELETED, null);
    }

    public static FileChangeRecord unchanged(FileAsset asset) {
        return new FileChangeRecord(asset, ChangeType.UNCHANGED, null);
    }
}
