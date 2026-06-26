package com.projvault.pkc.file;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DerivedDataCleanupServiceTest {

    @Test
    void removesDerivedDataOnlyForDeletedFiles() {
        DocChunkRepository chunks = mock(DocChunkRepository.class);
        DocImageRepository images = mock(DocImageRepository.class);
        ConfigItemRepository configs = mock(ConfigItemRepository.class);
        DerivedDataCleanupService service = new DerivedDataCleanupService(chunks, images, configs);

        FileAsset deleted = new FileAsset();
        deleted.setId(7L);
        FileAsset modified = new FileAsset();
        modified.setId(8L);

        service.cleanupDeleted(List.of(
                FileChangeRecord.deleted(deleted),
                FileChangeRecord.modified(modified)));

        verify(chunks).deleteByFileId(7L);
        verify(images).deleteByFileId(7L);
        verify(configs).deleteByFileId(7L);
        verify(chunks, never()).deleteByFileId(8L);
    }
}
