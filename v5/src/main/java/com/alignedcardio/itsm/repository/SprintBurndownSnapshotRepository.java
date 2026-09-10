package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.SprintBurndownSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprintBurndownSnapshotRepository extends JpaRepository<SprintBurndownSnapshot, UUID> {

    List<SprintBurndownSnapshot> findBySprintIdOrderBySnapshotDateAsc(UUID sprintId);
}
