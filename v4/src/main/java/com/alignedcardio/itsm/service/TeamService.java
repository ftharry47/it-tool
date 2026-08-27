package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.team.TeamCreateRequest;
import com.alignedcardio.itsm.api.team.TeamResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Team;
import com.alignedcardio.itsm.entity.TeamMember;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.TeamMemberRepository;
import com.alignedcardio.itsm.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AppUserRepository appUserRepository;

    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       AppUserRepository appUserRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public TeamResponse create(UUID orgId, UUID createdBy, TeamCreateRequest request) {
        Team team = new Team();
        team.setOrgId(orgId);
        team.setName(request.name());
        team.setDescription(request.description());
        team.setCreatedBy(createdBy);
        team.setUpdatedBy(createdBy);
        team.setUpdatedAt(OffsetDateTime.now());

        return toResponse(teamRepository.save(team));
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> list(UUID orgId) {
        return teamRepository.findByOrgIdAndDeletedAtIsNullOrderByName(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse get(UUID orgId, UUID id) {
        return teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Team not found"));
    }

    @Transactional
    public void addMember(UUID orgId, UUID teamId, UUID userId, UUID createdBy) {
        Team team = teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));
        appUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (teamMemberRepository.existsByTeamIdAndUserId(team.getId(), userId)) {
            return;
        }

        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setCreatedBy(createdBy);
        teamMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(UUID orgId, UUID teamId, UUID userId) {
        teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));
        teamMemberRepository.deleteByTeamIdAndUserId(teamId, userId);
    }

    private TeamResponse toResponse(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getCreatedBy(),
                team.getUpdatedBy());
    }
}
