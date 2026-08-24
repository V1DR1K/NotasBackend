package com.tomas.cuaderno.files;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID>, JpaSpecificationExecutor<FileMetadata> {
    interface FolderCount {
        UUID getFolderId();
        long getFileCount();
    }
    Page<FileMetadata> findByOwnerIdAndDeletedAtIsNull(UUID owner, Pageable page);
    Page<FileMetadata> findByOwnerIdAndFolderIdAndDeletedAtIsNull(UUID owner, UUID folder, Pageable page);
    Page<FileMetadata> findByOwnerIdAndDeletedAtIsNullAndNameContainingIgnoreCase(UUID owner, String search, Pageable page);
    Page<FileMetadata> findByOwnerIdAndFolderIdAndDeletedAtIsNullAndNameContainingIgnoreCase(UUID owner, UUID folder, String search, Pageable page);
    Optional<FileMetadata> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID owner);
    long countByOwnerIdAndFolderIdAndDeletedAtIsNull(UUID owner, UUID folder);
    @Query("select f.folderId as folderId, count(f) as fileCount from FileMetadata f where f.ownerId = :owner and f.folderId in :folders and f.deletedAt is null group by f.folderId")
    List<FolderCount> countByOwnerAndFolderIds(@Param("owner") UUID owner, @Param("folders") Collection<UUID> folders);
    long countByOwnerIdAndDeletedAtIsNull(UUID owner);
    @Query("select coalesce(sum(f.sizeBytes), 0) from FileMetadata f where f.ownerId = :owner and f.deletedAt is null")
    long sumSizeByOwnerIdAndDeletedAtIsNull(@Param("owner") UUID owner);
}
