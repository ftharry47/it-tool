package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.IssueCommentCreateRequest;
import com.alignedcardio.itsm.api.project.IssueCommentResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CommentAuthorType;
import com.alignedcardio.itsm.entity.Issue;
import com.alignedcardio.itsm.entity.IssueComment;
import com.alignedcardio.itsm.repository.IssueCommentRepository;
import com.alignedcardio.itsm.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueCommentServiceTest {

    @Mock
    private IssueCommentRepository issueCommentRepository;
    @Mock
    private IssueRepository issueRepository;

    @InjectMocks
    private IssueCommentService issueCommentService;

    @Test
    void createCommentPersistsAndReturns() {
        UUID orgId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();

        Issue issue = new Issue();
        issue.setId(issueId);
        issue.setOrgId(orgId);

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(issueCommentRepository.save(any(IssueComment.class)))
                .thenAnswer(i -> {
                    IssueComment c = i.getArgument(0);
                    c.setId(UUID.randomUUID());
                    return c;
                });

        IssueCommentResponse response = issueCommentService.create(
                orgId, UUID.randomUUID(), issueId, new IssueCommentCreateRequest("test comment"));

        assertEquals(issueId, response.issueId());
        assertEquals("test comment", response.body());

        ArgumentCaptor<IssueComment> captor = ArgumentCaptor.forClass(IssueComment.class);
        verify(issueCommentRepository).save(captor.capture());
        assertEquals(issue, captor.getValue().getIssue());
    }

    @Test
    void addAutomationCommentIsInternalAndSystemAttributed() {
        UUID orgId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();

        Issue issue = new Issue();
        issue.setId(issueId);
        issue.setOrgId(orgId);

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(issueCommentRepository.save(any(IssueComment.class)))
                .thenAnswer(i -> {
                    IssueComment c = i.getArgument(0);
                    c.setId(UUID.randomUUID());
                    return c;
                });

        IssueCommentResponse response = issueCommentService.addAutomationComment(
                orgId, createdBy, issueId, "Auto-generated note");

        assertEquals(issueId, response.issueId());
        assertEquals("Auto-generated note", response.body());
        assertFalse(response.isPublic());
        assertEquals(CommentAuthorType.AUTOMATION.name(), response.authorType());

        ArgumentCaptor<IssueComment> captor = ArgumentCaptor.forClass(IssueComment.class);
        verify(issueCommentRepository).save(captor.capture());

        IssueComment comment = captor.getValue();
        assertEquals("Auto-generated note", comment.getBody());
        assertEquals(createdBy, comment.getCreatedBy());
        assertFalse(comment.isPublic());
        assertEquals(CommentAuthorType.AUTOMATION, comment.getAuthorType());
    }

    @Test
    void listCommentsReturnsInOrder() {
        UUID orgId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();

        Issue issue = new Issue();
        issue.setId(issueId);
        issue.setOrgId(orgId);

        IssueComment c1 = new IssueComment();
        c1.setIssue(issue);
        c1.setBody("first");

        IssueComment c2 = new IssueComment();
        c2.setIssue(issue);
        c2.setBody("second");

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(issueCommentRepository.findByIssueIdOrderByCreatedAtDesc(issueId))
                .thenReturn(List.of(c2, c1));

        List<IssueCommentResponse> result = issueCommentService.list(orgId, issueId);

        assertEquals(2, result.size());
        assertEquals("second", result.get(0).body());
        assertEquals("first", result.get(1).body());
    }
}
