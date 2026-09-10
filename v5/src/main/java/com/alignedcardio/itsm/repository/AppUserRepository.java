package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.AppUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByOrgIdAndObjectId(UUID orgId, String objectId);

    Optional<AppUser> findByOrgIdAndEmail(UUID orgId, String email);

    Optional<AppUser> findByOrgIdAndEmailIgnoreCase(UUID orgId, String email);

    Optional<AppUser> findByOrgIdAndId(UUID orgId, UUID id);

    List<AppUser> findByOrgId(UUID orgId);

    @Query("SELECT u FROM AppUser u WHERE u.orgId = ?1 AND u.deletedAt IS NULL " +
            "AND (lower(u.displayName) LIKE lower(concat('%', ?2, '%')) OR lower(u.email) LIKE lower(concat('%', ?2, '%'))) " +
            "ORDER BY u.displayName")
    List<AppUser> searchByText(UUID orgId, String query, Pageable pageable);

    @Query("SELECT DISTINCT u FROM AppUser u JOIN u.userRoles ur JOIN ur.role r " +
            "WHERE u.orgId = ?1 AND u.deletedAt IS NULL AND r.name IN ?2")
    List<AppUser> findByOrgIdAndRoleNames(UUID orgId, List<String> roleNames);
}
