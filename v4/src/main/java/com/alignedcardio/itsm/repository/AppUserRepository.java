package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByOrgIdAndObjectId(UUID orgId, String objectId);

    Optional<AppUser> findByOrgIdAndEmail(UUID orgId, String email);

    List<AppUser> findByOrgId(UUID orgId);
}
