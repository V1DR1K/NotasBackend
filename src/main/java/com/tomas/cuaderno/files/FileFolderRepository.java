package com.tomas.cuaderno.files;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileFolderRepository extends JpaRepository<FileFolder, UUID> {
    Page<FileFolder> findByOwnerIdAndDeletedAtIsNull(UUID owner, Pageable page);
    Optional<FileFolder> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID owner);
    long countByOwnerIdAndDeletedAtIsNull(UUID owner);
}
