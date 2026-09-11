package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.NotificationPreference;
import com.alignedcardio.itsm.entity.TeamMember;
import com.alignedcardio.itsm.event.DomainEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.NotificationPreferenceRepository;
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
    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationHandler(NotificationService notificationService,
                               AppUserRepository appUserRepository,
                               TeamRepository teamRepository,
                               TeamMemberRepository teamMemberRepository,
                               NotificationTemplateBuilder templateBuilder,
                               NotificationPreferenceRepository preferenceRepository) {
        this.notificationService = notificationService;
        this.appUserRepository = appUserRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.templateBuilder = templateBuilder;
        this.preferenceRepository = preferenceRepository;
    }

    public void handle(DomainEvent event, JsonNode action, UUID actingUserId) {
        String channel = action.hasNonNull("channel") ? action.get("channel").asText().toUpperCase() : "BOTH";

        if (action.hasNonNull("role")) {
            for (UUID userId : resolveRoleUserIds(action.get("role").asText(), event)) {
                if (shouldSkip(event, userId)) continue;
                send(event, userId, channel, action);
            }
            return;
        }

        if (action.hasNonNull("teamId")) {
            for (UUID userId : resolveTeamUserIds(action.get("teamId").asText(), event)) {
                if (shouldSkip(event, userId)) continue;
                send(event, userId, channel, action);
            }
            return;
        }

        String userIdText = action.get("userId").asText();
        UUID userId = resolveUserId(userIdText, event);
        if (userId == null || shouldSkip(event, userId)) {
            return;
        }
        send(event, userId, channel, action);
    }

    private void send(DomainEvent event, UUID userId, String channel, JsonNode action) {
        NotificationContent content = templateBuilder.fromRule(action, event);
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

    private boolean shouldSkip(DomainEvent event, UUID userId) {
        if (userId == null) {
            return true;
        }
        String entity = event.triggerEntity();
        String type = event.triggerType();
        if ("INCIDENT".equals(entity) && ("STATUS_CHANGED".equals(type) || "PRIORITY_CHANGED".equals(type))) {
            Object requester = event.payload().get("requesterId");
            if (requester != null && userId.equals(toUuid(requester))) {
                NotificationPreference preference = preferenceRepository.findByUserId(userId)
                        .orElse(null);
                if (preference != null && !preference.isNotifyStatusChange()) {
                    return true;
                }
            }
        }
        return false;
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof String str) {
            try {
                return UUID.fromString(str);
            } catch (Exception ignored) {
            }
        }
        return null;
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
            if (value == null) {
                return null;
            }
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
