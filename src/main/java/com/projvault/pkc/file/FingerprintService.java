package com.projvault.pkc.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.projvault.pkc.scan.ScanMode;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P2 指纹服务：对 P1 枚举的文件列表做 sha256 增量对比，产出变更记录并持久化 pkc_file。
 *
 * 优化策略（UA fingerprint 思路）：
 * 若文件 size + mtime 均与 DB 记录一致，跳过重新计算 sha256（大文件友好）。
 * RENAMED 识别：sha256 命中已删除路径的旧记录 → 标记为 RENAMED 而非 ADDED+DELETED。
 */
@Service
public class FingerprintService {

    private static final Logger log = LoggerFactory.getLogger(FingerprintService.class);
    private static final int SAMPLE_COUNT = 16;
    private static final int SAMPLE_SIZE = 4096;

    private final FileAssetRepository fileAssetRepository;

    public FingerprintService(FileAssetRepository fileAssetRepository) {
        this.fileAssetRepository = fileAssetRepository;
    }

    /**
     * 对项目执行指纹增量扫描，返回完整变更记录列表。
     *
     * @param projectId   项目 ID
     * @param scanId      当前扫描任务 ID（用于 firstSeenScan / lastSeenScan）
     * @param validPaths  P1 枚举后的有效文件路径列表（绝对路径）
     * @param projectRoot 项目根目录
     */
    @Transactional
    public List<FileChangeRecord> fingerprint(Long projectId,
                                              Long scanId,
                                              List<Path> validPaths,
                                              Path projectRoot,
                                              ScanMode mode) {
        // 加载现有 DB 记录（按 relPath 索引）
        List<FileAsset> existing = fileAssetRepository.findByProjectIdAndDeletedFlagFalse(projectId);
        Map<String, FileAsset> byRelPath = new HashMap<>();
        Map<String, FileAsset> bySha256   = new HashMap<>();
        for (FileAsset a : existing) {
            byRelPath.put(a.getRelPath(), a);
            if (a.getSha256() != null) {
                bySha256.put(a.getSha256(), a);
            }
        }

        List<FileChangeRecord> changes = new ArrayList<>();
        Set<String> seenRelPaths = new HashSet<>();

        for (Path abs : validPaths) {
            String rel  = projectRoot.relativize(abs).toString().replace('\\', '/');
            String name = abs.getFileName().toString();
            String ext  = extensionOf(name);
            seenRelPaths.add(rel);

            long size, mtime;
            try {
                size  = Files.size(abs);
                mtime = Files.getLastModifiedTime(abs).toMillis();
            } catch (IOException e) {
                log.warn("无法读取文件属性，跳过: {}", abs);
                continue;
            }

            FileAsset db = byRelPath.get(rel);

            if (db != null) {
                if (mode == ScanMode.FULL) {
                    // FULL 模式：强制重新处理，不做 size+mtime 快速路径
                    String newHash = sha256(abs);
                    markModified(db, newHash, contentSignature(abs, size), size, mtime, scanId);
                    fileAssetRepository.save(db);
                    changes.add(FileChangeRecord.modified(db));
                    if (newHash != null) {
                        bySha256.put(newHash, db);
                    }
                } else {
                    // INCREMENTAL 模式：size+mtime 快速判断是否变化
                    if (db.getSize() == size && db.getMtime() == mtime) {
                        String signature = contentSignature(abs, size);
                        boolean needsBaseline = db.getContentSignature() == null;
                        boolean sampledChange = !needsBaseline && signature != null
                                && !signature.equals(db.getContentSignature());
                        if (needsBaseline || sampledChange) {
                            String newHash = sha256(abs);
                            if (newHash != null && !newHash.equals(db.getSha256())) {
                                markModified(db, newHash, signature, size, mtime, scanId);
                                fileAssetRepository.save(db);
                                changes.add(FileChangeRecord.modified(db));
                            } else {
                                db.setContentSignature(signature);
                                touchUnchanged(db, size, mtime, scanId);
                                fileAssetRepository.save(db);
                                changes.add(FileChangeRecord.unchanged(db));
                            }
                        } else {
                            touchUnchanged(db, size, mtime, scanId);
                            fileAssetRepository.save(db);
                            changes.add(FileChangeRecord.unchanged(db));
                        }
                    } else {
                        // size 或 mtime 变了，重新计算 sha256
                        String newHash = sha256(abs);
                        if (newHash != null && newHash.equals(db.getSha256())) {
                            // 只是 mtime 戳变了（如 touch），内容未变
                            db.setSize(size);
                            db.setMtime(mtime);
                            db.setContentSignature(contentSignature(abs, size));
                            db.setLastSeenScan(scanId);
                            db.setUpdatedAt(LocalDateTime.now());
                            fileAssetRepository.save(db);
                            changes.add(FileChangeRecord.unchanged(db));
                        } else {
                            markModified(db, newHash, contentSignature(abs, size), size, mtime, scanId);
                            fileAssetRepository.save(db);
                            changes.add(FileChangeRecord.modified(db));
                            if (newHash != null) {
                                bySha256.put(newHash, db);
                            }
                        }
                    }
                }
            } else {
                // 路径不在 DB：先计算 sha256，判断是否 RENAMED
                String hash = sha256(abs);
                FileAsset old = (hash != null) ? bySha256.get(hash) : null;

                if (old != null && old.isDeletedFlag()) {
                    // RENAMED：旧记录软删除态且 sha256 匹配
                    old.setRelPath(rel);
                    old.setName(name);
                    old.setExt(ext);
                    old.setCategory(FileCategoryResolver.resolve(ext));
                    old.setSize(size);
                    old.setMtime(mtime);
                    old.setContentSignature(contentSignature(abs, size));
                    old.setDeletedFlag(false);
                    old.setLastSeenScan(scanId);
                    old.setUpdatedAt(LocalDateTime.now());
                    fileAssetRepository.save(old);
                    changes.add(FileChangeRecord.renamed(old, byRelPath.entrySet().stream()
                            .filter(e -> e.getValue().getId().equals(old.getId()))
                            .map(Map.Entry::getKey)
                            .findFirst().orElse(null)));
                } else {
                    // ADDED
                    FileAsset a = new FileAsset();
                    a.setProjectId(projectId);
                    a.setRelPath(rel);
                    a.setName(name);
                    a.setExt(ext);
                    a.setCategory(FileCategoryResolver.resolve(ext));
                    a.setSize(size);
                    a.setMtime(mtime);
                    a.setSha256(hash);
                    a.setContentSignature(contentSignature(abs, size));
                    a.setFirstSeenScan(scanId);
                    a.setLastSeenScan(scanId);
                    a.setUpdatedAt(LocalDateTime.now());
                    fileAssetRepository.save(a);
                    changes.add(FileChangeRecord.added(a));
                    if (hash != null) {
                        bySha256.put(hash, a);
                    }
                }
            }
        }

        // 未出现在本次扫描的现有记录 → DELETED（软删除）
        for (Map.Entry<String, FileAsset> entry : byRelPath.entrySet()) {
            if (!seenRelPaths.contains(entry.getKey())) {
                FileAsset a = entry.getValue();
                a.setDeletedFlag(true);
                a.setUpdatedAt(LocalDateTime.now());
                fileAssetRepository.save(a);
                changes.add(FileChangeRecord.deleted(a));
            }
        }

        return changes;
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String sha256(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            log.warn("sha256 计算失败: {}", path);
            return null;
        }
    }

    private String contentSignature(Path path, long size) {
        if (size <= (long) SAMPLE_COUNT * SAMPLE_SIZE) {
            return sha256(path);
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(ByteBuffer.allocate(Long.BYTES).putLong(size).array());
            long maxStart = Math.max(0L, size - SAMPLE_SIZE);
            for (int i = 0; i < SAMPLE_COUNT; i++) {
                long position = i == SAMPLE_COUNT - 1
                        ? maxStart
                        : (maxStart * i) / (SAMPLE_COUNT - 1);
                ByteBuffer buffer = ByteBuffer.allocate(SAMPLE_SIZE);
                channel.position(position);
                while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                    // Continue until the sample is full or EOF is reached.
                }
                buffer.flip();
                md.update(ByteBuffer.allocate(Long.BYTES).putLong(position).array());
                md.update(buffer);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            log.warn("content signature failed: {}", path);
            return null;
        }
    }

    private void markModified(FileAsset asset,
                              String sha256,
                              String signature,
                              long size,
                              long mtime,
                              Long scanId) {
        asset.setSha256(sha256);
        asset.setContentSignature(signature);
        asset.setSize(size);
        asset.setMtime(mtime);
        asset.setParseStatus("PENDING");
        asset.setSummary(null);
        asset.setTags(null);
        asset.setDocType(null);
        asset.setRelevanceStatus(null);
        asset.setRelevanceScore(0.0);
        asset.setRelevanceReason(null);
        asset.setScopeType(null);
        asset.setScopeReason(null);
        asset.setLastSeenScan(scanId);
        asset.setUpdatedAt(LocalDateTime.now());
    }

    private void touchUnchanged(FileAsset asset, long size, long mtime, Long scanId) {
        asset.setSize(size);
        asset.setMtime(mtime);
        asset.setLastSeenScan(scanId);
        asset.setUpdatedAt(LocalDateTime.now());
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1)
                ? name.substring(dot + 1)
                : "";
    }
}
