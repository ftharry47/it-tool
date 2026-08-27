package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_preference")
public class NotificationPreference extends BaseEntity {

    public enum DigestMode {
        NONE, HOURLY, DAILY
    }

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false, unique = true)
    private java.util.UUID userId;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "email_address", length = 255)
    private String emailAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "digest_mode", nullable = false, length = 16)
    private DigestMode digestMode = DigestMode.NONE;

    public java.util.UUID getUserId() {
        return userId;
    }

    public void setUserId(java.util.UUID userId) {
        this.userId = userId;
    }

    public boolean isInAppEnabled() {
        return inAppEnabled;
    }

    public void setInAppEnabled(boolean inAppEnabled) {
        this.inAppEnabled = inAppEnabled;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public DigestMode getDigestMode() {
        return digestMode;
    }

    public void setDigestMode(DigestMode digestMode) {
        this.digestMode = digestMode;
    }
}
