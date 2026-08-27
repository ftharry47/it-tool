package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, UUID> {

    List<Sprint> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<Sprint> findByProjectIdAndId(UUID projectId, UUID id);
}
