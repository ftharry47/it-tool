package com.alignedcardio.itsm.api.notification;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.NotificationPreference;
import com.alignedcardio.itsm.repository.NotificationPreferenceRepository;
import com.alignedcardio.itsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserService userService;

    public NotificationPreferenceController(NotificationPreferenceRepository preferenceRepository,
                                            UserService userService) {
        this.preferenceRepository = preferenceRepository;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<NotificationPreferenceResponse> get(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = userService.syncFromJwt(jwt);
        NotificationPreference preference = preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> createDefault(user));
        return ResponseEntity.ok(toResponse(preference));
    }

    @PutMapping
    public ResponseEntity<NotificationPreferenceResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NotificationPreferenceUpdateRequest request) {
        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }
        AppUser user = userService.syncFromJwt(jwt);
        NotificationPreference preference = preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> createDefault(user));

        preference.setInAppEnabled(request.inAppEnabled());
        preference.setEmailEnabled(request.emailEnabled());
        preference.setEmailAddress(request.emailAddress());
        preference.setDigestMode(parseDigestMode(request.digestMode()));
        preference.setNotifyStatusChange(request.notifyStatusChange());
        preference.setNotifyAssignment(request.notifyAssignment());
        preference.setNotifyComment(request.notifyComment());
        preference.setNotifyMention(request.notifyMention());
        if (request.pushEnabled() != null) {
            preference.setPushEnabled(request.pushEnabled());
        }

        preferenceRepository.save(preference);
        return ResponseEntity.ok(toResponse(preference));
    }

    private NotificationPreference createDefault(AppUser user) {
        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(user.getOrgId());
        preference.setUserId(user.getId());
        preference.setInAppEnabled(true);
        preference.setEmailEnabled(true);
        preference.setEmailAddress(user.getEmail());
        preference.setDigestMode(NotificationPreference.DigestMode.NONE);
        preference.setNotifyStatusChange(true);
        preference.setNotifyAssignment(true);
        preference.setNotifyComment(true);
        preference.setNotifyMention(true);
        return preferenceRepository.save(preference);
    }

    private NotificationPreference.DigestMode parseDigestMode(String value) {
        if (value == null) {
            return NotificationPreference.DigestMode.NONE;
        }
        try {
            return NotificationPreference.DigestMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid digest mode. Valid values: NONE, HOURLY, DAILY");
        }
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.isInAppEnabled(),
                preference.isEmailEnabled(),
                preference.getEmailAddress(),
                preference.getDigestMode().name(),
                preference.isNotifyStatusChange(),
                preference.isNotifyAssignment(),
                preference.isNotifyComment(),
                preference.isNotifyMention(),
                preference.isPushEnabled()
        );
    }
}
