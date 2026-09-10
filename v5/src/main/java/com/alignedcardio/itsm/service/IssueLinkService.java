package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.IssueLinkCreateRequest;
import com.alignedcardio.itsm.api.project.IssueLinkResponse;
import com.alignedcardio.itsm.entity.Issue;
import com.alignedcardio.itsm.entity.IssueLink;
import com.alignedcardio.itsm.repository.IssueLinkRepository;
import com.alignedcardio.itsm.repository.IssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IssueLinkService {

    private final IssueLinkRepository issueLinkRepository;
    private final IssueRepository issueRepository;

    public IssueLinkService(IssueLinkRepository issueLinkRepository, IssueRepository issueRepository) {
        this.issueLinkRepository = issueLinkRepository;
        this.issueRepository = issueRepository;
    }

    @Transactional
    public IssueLinkResponse create(UUID orgId, UUID createdBy, UUID fromIssueId, IssueLinkCreateRequest request) {
        Issue from = issueRepository.findById(fromIssueId)
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Source issue not found"));
        Issue to = issueRepository.findById(request.toIssueId())
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Target issue not found"));

        IssueLink link = new IssueLink();
        link.setOrgId(orgId);
        link.setFromIssue(from);
        link.setToIssue(to);
        link.setLinkType(request.linkType());
        link.setCreatedBy(createdBy);
        link.setUpdatedBy(createdBy);
        return toResponse(issueLinkRepository.save(link));
    }

    @Transactional(readOnly = true)
    public List<IssueLinkResponse> list(UUID orgId, UUID issueId) {
        Issue issue = issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Issue not found"));

        List<IssueLink> outgoing = issueLinkRepository.findByFromIssueId(issue.getId());
        List<IssueLink> incoming = issueLinkRepository.findByToIssueId(issue.getId());
        List<IssueLink> all = new java.util.ArrayList<>();
        all.addAll(outgoing);
        all.addAll(incoming);
        return all.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(UUID orgId, UUID linkId) {
        IssueLink link = issueLinkRepository.findById(linkId)
                .filter(l -> l.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Issue link not found"));
        issueLinkRepository.delete(link);
    }

    private IssueLinkResponse toResponse(IssueLink l) {
        return new IssueLinkResponse(
                l.getId(),
                l.getFromIssue().getId(),
                l.getToIssue().getId(),
                l.getToIssue().getKey(),
                l.getToIssue().getSummary(),
                l.getLinkType()
        );
    }
}
