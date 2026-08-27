package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.IssueLinkCreateRequest;
import com.alignedcardio.itsm.api.project.IssueLinkResponse;
import com.alignedcardio.itsm.entity.Issue;
import com.alignedcardio.itsm.entity.IssueLink;
import com.alignedcardio.itsm.repository.IssueLinkRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueLinkServiceTest {

    @Mock
    private IssueLinkRepository issueLinkRepository;
    @Mock
    private IssueRepository issueRepository;

    @InjectMocks
    private IssueLinkService issueLinkService;

    private Issue issue(UUID id, UUID orgId) {
        Issue i = new Issue();
        i.setId(id);
        i.setOrgId(orgId);
        i.setKey("PROJ-" + id.toString().substring(0, 4));
        i.setSummary("issue " + id);
        return i;
    }

    @Test
    void createLinkWithEveryLinkType() {
        UUID orgId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        Issue from = issue(fromId, orgId);
        Issue to = issue(toId, orgId);

        when(issueRepository.findById(fromId)).thenReturn(Optional.of(from));
        when(issueRepository.findById(toId)).thenReturn(Optional.of(to));
        when(issueLinkRepository.save(any(IssueLink.class)))
                .thenAnswer(i -> {
                    IssueLink l = i.getArgument(0);
                    l.setId(UUID.randomUUID());
                    return l;
                });

        for (IssueLink.LinkType type : IssueLink.LinkType.values()) {
            issueLinkService.create(orgId, UUID.randomUUID(), fromId,
                    new IssueLinkCreateRequest(toId, type));

            ArgumentCaptor<IssueLink> captor = ArgumentCaptor.forClass(IssueLink.class);
            verify(issueLinkRepository, atLeastOnce()).save(captor.capture());
            IssueLink saved = captor.getValue();
            assertEquals(type, saved.getLinkType());
            assertEquals(from, saved.getFromIssue());
            assertEquals(to, saved.getToIssue());
        }
    }

    @Test
    void listIncludesBothIncomingAndOutgoingLinks() {
        UUID orgId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();

        Issue i = issue(issueId, orgId);

        Issue a = issue(UUID.randomUUID(), orgId);
        Issue b = issue(UUID.randomUUID(), orgId);

        IssueLink out = new IssueLink();
        out.setFromIssue(i);
        out.setToIssue(a);
        out.setLinkType(IssueLink.LinkType.BLOCKS);

        IssueLink in = new IssueLink();
        in.setFromIssue(b);
        in.setToIssue(i);
        in.setLinkType(IssueLink.LinkType.BLOCKED_BY);

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(i));
        when(issueLinkRepository.findByFromIssueId(issueId)).thenReturn(List.of(out));
        when(issueLinkRepository.findByToIssueId(issueId)).thenReturn(List.of(in));

        List<IssueLinkResponse> result = issueLinkService.list(orgId, issueId);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> r.toIssueId().equals(a.getId())));
        assertTrue(result.stream().anyMatch(r -> r.fromIssueId().equals(b.getId())));
    }
}
