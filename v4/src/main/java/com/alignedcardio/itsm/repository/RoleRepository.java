package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByOrgIdAndName(UUID orgId, String name);
}
