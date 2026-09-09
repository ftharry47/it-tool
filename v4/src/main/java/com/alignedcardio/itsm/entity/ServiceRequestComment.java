package com.alignedcardio.itsm.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "service_request_comment")
public class ServiceRequestComment extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @NotNull
    @Column(name = "body", length = 4000, nullable = false)
    private String body;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", length = 20, nullable = false)
    private CommentAuthorType authorType = CommentAuthorType.USER;

    public ServiceRequest getServiceRequest() {
        return serviceRequest;
    }

    public void setServiceRequest(ServiceRequest serviceRequest) {
        this.serviceRequest = serviceRequest;
    }

    public AppUser getAuthor() {
        return author;
    }

    public void setAuthor(AppUser author) {
        this.author = author;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public CommentAuthorType getAuthorType() {
        return authorType;
    }

    public void setAuthorType(CommentAuthorType authorType) {
        this.authorType = authorType;
    }
}
