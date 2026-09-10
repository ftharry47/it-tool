package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.TeamMember;
import com.alignedcardio.itsm.entity.TeamMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {

    List<TeamMember> findByTeamId(UUID teamId);

    List<TeamMember> findByUserId(UUID userId);

    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);

    void deleteByTeamIdAndUserId(UUID teamId, UUID userId);
}
