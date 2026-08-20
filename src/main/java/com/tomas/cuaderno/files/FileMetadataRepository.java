package com.tomas.cuaderno.files;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID>, JpaSpecificationExecutor<FileMetadata> {
    Page<FileMetadata> findByOwnerIdAndDeletedAtIsNull(UUID owner, Pageable page);
    Page<FileMetadata> findByOwnerIdAndFolderIdAndDeletedAtIsNull(UUID owner, UUID folder, Pageable page);
    Page<FileMetadata> findByOwnerIdAndDeletedAtIsNullAndNameContainingIgnoreCase(UUID owner, String search, Pageable page);
    Page<FileMetadata> findByOwnerIdAndFolderIdAndDeletedAtIsNullAndNameContainingIgnoreCase(UUID owner, UUID folder, String search, Pageable page);
    Optional<FileMetadata> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID owner);
    long countByOwnerIdAndFolderIdAndDeletedAtIsNull(UUID owner, UUID folder);
    long countByOwnerIdAndDeletedAtIsNull(UUID owner);
}
