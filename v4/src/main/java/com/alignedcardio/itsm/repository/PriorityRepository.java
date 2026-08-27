package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Priority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriorityRepository extends JpaRepository<Priority, UUID> {

    List<Priority> findByOrgIdAndStatusOrderByDisplayOrderAsc(UUID orgId, Priority.Status status);

    Optional<Priority> findByOrgIdAndName(UUID orgId, String name);
}
