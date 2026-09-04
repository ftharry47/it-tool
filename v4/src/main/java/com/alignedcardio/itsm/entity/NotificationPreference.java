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

    @Column(name = "notify_status_change", nullable = false)
    private boolean notifyStatusChange = true;

    @Column(name = "notify_assignment", nullable = false)
    private boolean notifyAssignment = true;

    @Column(name = "notify_comment", nullable = false)
    private boolean notifyComment = true;

    @Column(name = "notify_mention", nullable = false)
    private boolean notifyMention = true;

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

    public boolean isNotifyStatusChange() {
        return notifyStatusChange;
    }

    public void setNotifyStatusChange(boolean notifyStatusChange) {
        this.notifyStatusChange = notifyStatusChange;
    }

    public boolean isNotifyAssignment() {
        return notifyAssignment;
    }

    public void setNotifyAssignment(boolean notifyAssignment) {
        this.notifyAssignment = notifyAssignment;
    }

    public boolean isNotifyComment() {
        return notifyComment;
    }

    public void setNotifyComment(boolean notifyComment) {
        this.notifyComment = notifyComment;
    }

    public boolean isNotifyMention() {
        return notifyMention;
    }

    public void setNotifyMention(boolean notifyMention) {
        this.notifyMention = notifyMention;
    }
}
