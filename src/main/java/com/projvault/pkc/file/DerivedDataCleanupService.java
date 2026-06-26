package com.projvault.pkc.file;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DerivedDataCleanupService {

    private final DocChunkRepository docChunkRepository;
    private final DocImageRepository docImageRepository;
    private final ConfigItemRepository configItemRepository;

    public DerivedDataCleanupService(DocChunkRepository docChunkRepository,
                                     DocImageRepository docImageRepository,
                                     ConfigItemRepository configItemRepository) {
        this.docChunkRepository = docChunkRepository;
        this.docImageRepository = docImageRepository;
        this.configItemRepository = configItemRepository;
    }

    @Transactional
    public void cleanupDeleted(List<FileChangeRecord> changes) {
        for (FileChangeRecord change : changes) {
            if (change.changeType() != ChangeType.DELETED) {
                continue;
            }
            Long fileId = change.asset().getId();
            docChunkRepository.deleteByFileId(fileId);
            docImageRepository.deleteByFileId(fileId);
            configItemRepository.deleteByFileId(fileId);
        }
    }
}
