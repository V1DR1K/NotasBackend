package com.tomas.cuaderno.auth;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CentralUserAppAccessRepository extends JpaRepository<CentralUserAppAccess, UUID> {
    Optional<CentralUserAppAccess> findByUserIdAndAppCode(UUID userId, String appCode);
    List<CentralUserAppAccess> findAllByUserIdIn(Collection<UUID> userIds);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM central_user_app_access grant_row
                JOIN central_auth_users identity_row ON identity_row.id = grant_row.user_id
                WHERE grant_row.user_id = :userId
                  AND grant_row.app_code = :appCode
                  AND grant_row.enabled = TRUE
                  AND identity_row.enabled = TRUE
                  AND identity_row.deleted_at IS NULL
            )
            """, nativeQuery = true)
    boolean hasActiveAccess(@Param("userId") UUID userId, @Param("appCode") String appCode);

    @Query(value = """
            SELECT COUNT(*)
            FROM central_user_app_access grant_row
            JOIN central_auth_users identity_row ON identity_row.id = grant_row.user_id
            WHERE grant_row.app_code = 'notes'
              AND grant_row.role = 'ADMIN'
              AND grant_row.enabled = TRUE
              AND identity_row.enabled = TRUE
              AND identity_row.deleted_at IS NULL
              AND grant_row.user_id <> :excludedUserId
            """, nativeQuery = true)
    long countOtherActiveNotesAdmins(@Param("excludedUserId") UUID excludedUserId);
}
