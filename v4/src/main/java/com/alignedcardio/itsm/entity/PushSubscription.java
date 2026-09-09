package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Entity
@Table(name = "push_subscription")
public class PushSubscription extends BaseEntity {

    @NotNull
    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @NotNull
    @Column(name = "endpoint", nullable = false, columnDefinition = "text")
    private String endpoint;

    @NotNull
    @Column(name = "p256dh", nullable = false, columnDefinition = "text")
    private String p256dh;

    @NotNull
    @Column(name = "auth", nullable = false, columnDefinition = "text")
    private String auth;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
