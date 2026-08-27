package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

    public enum Channel {
        IN_APP, EMAIL, BOTH
    }

    public enum DeliveryStatus {
        PENDING, SENT, FAILED
    }

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id", columnDefinition = "uuid")
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private Channel channel = Channel.BOTH;

    @Enumerated(EnumType.STRING)
    @Column(name = "in_app_status", nullable = false, length = 16)
    private DeliveryStatus inAppStatus = DeliveryStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", length = 16)
    private DeliveryStatus emailStatus;

    @Column(name = "read_at", columnDefinition = "timestamptz")
    private OffsetDateTime readAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public DeliveryStatus getInAppStatus() {
        return inAppStatus;
    }

    public void setInAppStatus(DeliveryStatus inAppStatus) {
        this.inAppStatus = inAppStatus;
    }

    public DeliveryStatus getEmailStatus() {
        return emailStatus;
    }

    public void setEmailStatus(DeliveryStatus emailStatus) {
        this.emailStatus = emailStatus;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(OffsetDateTime readAt) {
        this.readAt = readAt;
    }
}
