package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.TeamMember;
import com.alignedcardio.itsm.event.DomainEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.TeamMemberRepository;
import com.alignedcardio.itsm.repository.TeamRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class NotificationHandler {

    private static final List<String> ROLE_HIERARCHY = List.of(
            "END_USER", "AGENT", "TEAM_LEAD", "ADMIN", "SUPER_ADMIN");

    private final NotificationService notificationService;
    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final NotificationTemplateBuilder templateBuilder;

    public NotificationHandler(NotificationService notificationService,
                               AppUserRepository appUserRepository,
                               TeamRepository teamRepository,
                               TeamMemberRepository teamMemberRepository,
                               NotificationTemplateBuilder templateBuilder) {
        this.notificationService = notificationService;
        this.appUserRepository = appUserRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.templateBuilder = templateBuilder;
    }

    public void handle(DomainEvent event, JsonNode action, UUID actingUserId) {
        NotificationContent content = templateBuilder.fromRule(action, event);
        String channel = action.hasNonNull("channel") ? action.get("channel").asText().toUpperCase() : "BOTH";

        if (action.hasNonNull("role")) {
            for (UUID userId : resolveRoleUserIds(action.get("role").asText(), event)) {
                send(event, userId, content, channel);
            }
            return;
        }

        if (action.hasNonNull("teamId")) {
            for (UUID userId : resolveTeamUserIds(action.get("teamId").asText(), event)) {
                send(event, userId, content, channel);
            }
            return;
        }

        String userIdText = action.get("userId").asText();
        send(event, resolveUserId(userIdText, event), content, channel);
    }

    private void send(DomainEvent event, UUID userId, NotificationContent content, String channel) {
        NotificationRequest request = new NotificationRequest(
                event.orgId(),
                userId,
                event.triggerType(),
                content.inAppSubject(),
                content.inAppBody(),
                event.triggerEntity(),
                event.entityId(),
                Notification.Channel.valueOf(channel),
                content);

        notificationService.send(request);
    }

    private List<UUID> resolveRoleUserIds(String roleName, DomainEvent event) {
        int tier = ROLE_HIERARCHY.indexOf(roleName.toUpperCase());
        if (tier < 0) {
            throw new IllegalArgumentException("Unknown role for notification targeting: " + roleName);
        }
        List<String> roleNames = ROLE_HIERARCHY.subList(tier, ROLE_HIERARCHY.size());
        return appUserRepository.findByOrgIdAndRoleNames(event.orgId(), roleNames).stream()
                .map(AppUser::getId)
                .distinct()
                .toList();
    }

    private List<UUID> resolveTeamUserIds(String teamIdText, DomainEvent event) {
        UUID teamId = UUID.fromString(teamIdText);
        teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(event.orgId(), teamId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Team not found in this org for notification targeting: " + teamId));
        return teamMemberRepository.findByTeamId(teamId).stream()
                .map(TeamMember::getUserId)
                .distinct()
                .toList();
    }

    private UUID resolveUserId(String userIdText, DomainEvent event) {
        String placeholder = unwrapPlaceholder(userIdText);
        if (placeholder != null) {
            Object value = event.payload().get(placeholder);
            if (value instanceof UUID uuid) {
                return uuid;
            }
            if (value instanceof String str) {
                return UUID.fromString(str);
            }
            throw new IllegalArgumentException("Payload does not contain a valid userId placeholder: " + placeholder);
        }
        return UUID.fromString(userIdText);
    }

    private String unwrapPlaceholder(String value) {
        if (value != null && value.startsWith("{{") && value.endsWith("}}")) {
            return value.substring(2, value.length() - 2).trim();
        }
        return null;
    }
}
