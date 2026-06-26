package com.projvault.pkc.file;

import com.projvault.pkc.scan.ScanMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FingerprintServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsReplacementWhenSizeAndMtimeArePreserved() throws Exception {
        Path file = tempDir.resolve("scope.md");
        String original = "A".repeat(80_000);
        String replacement = "B" + "A".repeat(79_999);
        FileTime fixedTime = FileTime.fromMillis(1_700_000_000_000L);
        Files.writeString(file, original);
        Files.setLastModifiedTime(file, fixedTime);

        FileAssetRepository repository = mock(FileAssetRepository.class);
        when(repository.findByProjectIdAndDeletedFlagFalse(1L))
                .thenReturn(List.of())
                .thenAnswer(invocation -> List.of(savedAsset));
        when(repository.save(any(FileAsset.class))).thenAnswer(invocation -> {
            FileAsset asset = invocation.getArgument(0);
            if (asset.getId() == null) {
                asset.setId(7L);
                savedAsset = asset;
            }
            return asset;
        });

        FingerprintService service = new FingerprintService(repository);
        List<FileChangeRecord> first = service.fingerprint(
                1L, 10L, List.of(file), tempDir, ScanMode.INCREMENTAL);
        String originalHash = first.get(0).asset().getSha256();
        savedAsset.setSummary("旧摘要");

        Files.writeString(file, replacement);
        Files.setLastModifiedTime(file, fixedTime);
        List<FileChangeRecord> second = service.fingerprint(
                1L, 11L, List.of(file), tempDir, ScanMode.INCREMENTAL);

        assertThat(second).hasSize(1);
        assertThat(second.get(0).changeType()).isEqualTo(ChangeType.MODIFIED);
        assertThat(second.get(0).asset().getSha256()).isNotEqualTo(originalHash);
        assertThat(second.get(0).asset().getSummary()).isNull();
        assertThat(second.get(0).asset().getParseStatus()).isEqualTo("PENDING");
    }

    private FileAsset savedAsset;
}
