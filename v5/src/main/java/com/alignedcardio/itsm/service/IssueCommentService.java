package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.IssueCommentCreateRequest;
import com.alignedcardio.itsm.api.project.IssueCommentResponse;
import com.alignedcardio.itsm.entity.CommentAuthorType;
import com.alignedcardio.itsm.entity.Issue;
import com.alignedcardio.itsm.entity.IssueComment;
import com.alignedcardio.itsm.repository.IssueCommentRepository;
import com.alignedcardio.itsm.repository.IssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IssueCommentService {

    private final IssueCommentRepository issueCommentRepository;
    private final IssueRepository issueRepository;

    public IssueCommentService(IssueCommentRepository issueCommentRepository, IssueRepository issueRepository) {
        this.issueCommentRepository = issueCommentRepository;
        this.issueRepository = issueRepository;
    }

    @Transactional
    public IssueCommentResponse create(UUID orgId, UUID createdBy, UUID issueId, IssueCommentCreateRequest request) {
        Issue issue = issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Issue not found"));
        IssueComment comment = new IssueComment();
        comment.setOrgId(orgId);
        comment.setIssue(issue);
        comment.setBody(request.body());
        comment.setPublic(true);
        comment.setAuthorType(CommentAuthorType.USER);
        comment.setCreatedBy(createdBy);
        comment.setUpdatedBy(createdBy);
        return toResponse(issueCommentRepository.save(comment));
    }

    @Transactional
    public IssueCommentResponse addAutomationComment(UUID orgId, UUID createdBy, UUID issueId, String body) {
        Issue issue = issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Issue not found"));

        IssueComment comment = new IssueComment();
        comment.setOrgId(orgId);
        comment.setIssue(issue);
        comment.setBody(body);
        comment.setPublic(false);
        comment.setAuthorType(CommentAuthorType.AUTOMATION);
        comment.setCreatedBy(createdBy);
        comment.setUpdatedBy(createdBy);
        return toResponse(issueCommentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public List<IssueCommentResponse> list(UUID orgId, UUID issueId) {
        Issue issue = issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Issue not found"));
        return issueCommentRepository.findByIssueIdOrderByCreatedAtDesc(issue.getId())
                .stream().map(this::toResponse).toList();
    }

    private IssueCommentResponse toResponse(IssueComment c) {
        return new IssueCommentResponse(
                c.getId(),
                c.getIssue().getId(),
                c.getBody(),
                c.getCreatedBy(),
                c.isPublic(),
                c.getAuthorType().name(),
                c.getCreatedAt()
        );
    }
}
