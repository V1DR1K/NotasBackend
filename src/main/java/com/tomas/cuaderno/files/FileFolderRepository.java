package com.tomas.cuaderno.files;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileFolderRepository extends JpaRepository<FileFolder, UUID> {
    Page<FileFolder> findByOwnerIdAndDeletedAtIsNull(UUID owner, Pageable page);
    @Query("select f from FileFolder f where f.ownerId = :owner and f.id in :ids and f.deletedAt is null")
    List<FileFolder> findActiveByOwnerAndIds(@Param("owner") UUID owner, @Param("ids") Collection<UUID> ids);
    Optional<FileFolder> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID owner);
    long countByOwnerIdAndDeletedAtIsNull(UUID owner);
}
