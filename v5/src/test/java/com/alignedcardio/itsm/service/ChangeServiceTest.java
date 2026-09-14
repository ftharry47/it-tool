package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.ChangeApproval;
import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.Location;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.ChangeApprovalRepository;
import com.alignedcardio.itsm.repository.ChangeRequestRepository;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.repository.ProblemRepository;
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeServiceTest {

    @Mock
    private ChangeRequestRepository changeRequestRepository;

    @Mock
    private ChangeApprovalRepository changeApprovalRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationTemplateBuilder notificationTemplateBuilder;

    @Mock
    private SlaEngine slaEngine;

    @InjectMocks
    private ChangeService changeService;

    private final com.alignedcardio.itsm.service.notification.NotificationContent dummyContent =
            new com.alignedcardio.itsm.service.notification.NotificationContent("", "", "", "", "", "");

    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        lenient().when(notificationTemplateBuilder.forEvent(any(), any())).thenReturn(dummyContent);
    }

    private AppUser userWithRole(String roleName) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail(roleName.toLowerCase() + "@example.com");
        user.setDisplayName(roleName);
        Role role = new Role();
        role.setName(roleName);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        user.getUserRoles().add(userRole);
        return user;
    }

    private ChangeRequest changeWithTypeAndStatus(ChangeRequest.ChangeType type, ChangeRequest.Status status, String pir) {
        ChangeRequest change = new ChangeRequest();
        change.setId(UUID.randomUUID());
        change.setOrgId(orgId);
        change.setNumber("CR-1");
        change.setTitle("Change");
        change.setChangeType(type);
        change.setRisk(ChangeRequest.Risk.MEDIUM);
        change.setStatus(status);
        change.setPostImplementationReview(pir);
        return change;
    }

    private ChangeApproval approvalFor(ChangeRequest change, AppUser approver, int sequenceOrder, ChangeApproval.Status status) {
        ChangeApproval approval = new ChangeApproval();
        approval.setId(UUID.randomUUID());
        approval.setChangeRequest(change);
        approval.setApprover(approver);
        approval.setSequenceOrder(sequenceOrder);
        approval.setStatus(status);
        return approval;
    }

    @Test
    void randomAgentCannotApproveNormalChange() {
        AppUser agent = userWithRole("AGENT");
        AppUser designatedApprover = userWithRole("ADMIN");
        ChangeRequest change = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.PENDING_APPROVAL, null);
        ChangeApproval approval = approvalFor(change, designatedApprover, 1, ChangeApproval.Status.PENDING);

        when(changeRequestRepository.findByOrgIdAndId(orgId, change.getId())).thenReturn(Optional.of(change));
        when(changeApprovalRepository.findByChangeRequestIdAndSequenceOrder(change.getId(), 1))
                .thenReturn(Optional.of(approval));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> changeService.approve(agent, orgId, change.getId(), 1, "approved"));
        assertEquals("Only the designated approver or an administrator can approve this change", ex.getMessage());
    }

    @Test
    void designatedTeamLeadCanApproveNormalChange() {
        AppUser teamLead = userWithRole("TEAM_LEAD");
        ChangeRequest change = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.PENDING_APPROVAL, null);
        ChangeApproval approval = approvalFor(change, teamLead, 1, ChangeApproval.Status.PENDING);
        List<ChangeApproval> approvals = new ArrayList<>(List.of(approval));

        when(changeRequestRepository.findByOrgIdAndId(orgId, change.getId())).thenReturn(Optional.of(change));
        when(changeApprovalRepository.findByChangeRequestIdAndSequenceOrder(change.getId(), 1))
                .thenReturn(Optional.of(approval));
        when(changeApprovalRepository.findByChangeRequestIdOrderBySequenceOrderAsc(change.getId()))
                .thenReturn(approvals);
        when(changeApprovalRepository.save(any(ChangeApproval.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(changeRequestRepository.save(change)).thenReturn(change);

        changeService.approve(teamLead, orgId, change.getId(), 1, "approved");

        assertEquals(ChangeRequest.Status.APPROVED, change.getStatus());
    }

    @Test
    void emergencyApprovalRequiresAdmin_notTeamLead() {
        AppUser teamLead = userWithRole("TEAM_LEAD");
        ChangeRequest change = changeWithTypeAndStatus(ChangeRequest.ChangeType.EMERGENCY, ChangeRequest.Status.PENDING_APPROVAL, null);
        ChangeApproval approval = approvalFor(change, teamLead, 1, ChangeApproval.Status.PENDING);

        when(changeRequestRepository.findByOrgIdAndId(orgId, change.getId())).thenReturn(Optional.of(change));
        when(changeApprovalRepository.findByChangeRequestIdAndSequenceOrder(change.getId(), 1))
                .thenReturn(Optional.of(approval));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> changeService.approve(teamLead, orgId, change.getId(), 1, "approved"));
        assertEquals("Approver does not have the required role for this change type", ex.getMessage());
    }

    @Test
    void adminCanApproveEmergencyAndMovesToInProgress() {
        AppUser admin = userWithRole("ADMIN");
        ChangeRequest change = changeWithTypeAndStatus(ChangeRequest.ChangeType.EMERGENCY, ChangeRequest.Status.PENDING_APPROVAL, null);
        ChangeApproval approval = approvalFor(change, admin, 1, ChangeApproval.Status.PENDING);
        List<ChangeApproval> approvals = new ArrayList<>(List.of(approval));

        when(changeRequestRepository.findByOrgIdAndId(orgId, change.getId())).thenReturn(Optional.of(change));
        when(changeApprovalRepository.findByChangeRequestIdAndSequenceOrder(change.getId(), 1))
                .thenReturn(Optional.of(approval));
        when(changeApprovalRepository.findByChangeRequestIdOrderBySequenceOrderAsc(change.getId()))
                .thenReturn(approvals);
        when(changeApprovalRepository.save(any(ChangeApproval.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(changeRequestRepository.save(change)).thenReturn(change);

        changeService.approve(admin, orgId, change.getId(), 1, "approved");

        assertEquals(ChangeRequest.Status.IN_PROGRESS, change.getStatus());
    }

    @Test
    void completedToClosedRequiresPostImplementationReview() {
        ChangeRequest change = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.COMPLETED, null);
        when(changeRequestRepository.findByOrgIdAndId(orgId, change.getId())).thenReturn(Optional.of(change));

        AppUser admin = userWithRole("ADMIN");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> changeService.updateStatus(admin, orgId, change.getId(), ChangeRequest.Status.CLOSED));
        assertEquals("Change cannot be closed without post_implementation_review", ex.getMessage());
    }

    @Test
    void agentCannotCloseChangeEvenWithReview() {
        ChangeRequest change = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.COMPLETED, "Review done");
        when(changeRequestRepository.findByOrgIdAndId(orgId, change.getId())).thenReturn(Optional.of(change));

        AppUser agent = userWithRole("AGENT");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> changeService.updateStatus(agent, orgId, change.getId(), ChangeRequest.Status.CLOSED));
        assertEquals("Only ADMIN or SUPER_ADMIN can close a change request", ex.getMessage());
    }

    @Test
    void adminCanCloseChangeWithReview() {
        ChangeRequest change = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.COMPLETED, "Review done");
        when(changeRequestRepository.findByOrgIdAndId(orgId, change.getId())).thenReturn(Optional.of(change));
        when(changeRequestRepository.save(change)).thenReturn(change);
        when(changeApprovalRepository.findByChangeRequestIdOrderBySequenceOrderAsc(change.getId()))
                .thenReturn(List.of());

        AppUser admin = userWithRole("ADMIN");

        var response = changeService.updateStatus(admin, orgId, change.getId(), ChangeRequest.Status.CLOSED);

        assertEquals(ChangeRequest.Status.CLOSED, response.status());
    }

    @Test
    void calendarDetectsSameLocationOverlap() {
        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setName("HQ");

        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusHours(2);

        ChangeRequest a = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.SCHEDULED, null);
        a.setPlannedStart(start);
        a.setPlannedEnd(end);
        a.setLocation(location);

        ChangeRequest b = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.SCHEDULED, null);
        b.setPlannedStart(start.plusMinutes(30));
        b.setPlannedEnd(end.plusMinutes(30));
        b.setLocation(location);

        when(changeRequestRepository.findByOrgIdAndStatusIn(orgId, List.of(ChangeRequest.Status.SCHEDULED, ChangeRequest.Status.IN_PROGRESS)))
                .thenReturn(List.of(a, b));

        var response = changeService.getCalendar(orgId, start.minusHours(1), end.plusHours(2));

        assertEquals(1, response.conflicts().size());
    }

    @Test
    void calendarDoesNotFlagDifferentLocationOverlap() {
        Location locA = new Location();
        locA.setId(UUID.randomUUID());
        locA.setName("Site A");

        Location locB = new Location();
        locB.setId(UUID.randomUUID());
        locB.setName("Site B");

        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusHours(2);

        ChangeRequest a = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.SCHEDULED, null);
        a.setPlannedStart(start);
        a.setPlannedEnd(end);
        a.setLocation(locA);

        ChangeRequest b = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.SCHEDULED, null);
        b.setPlannedStart(start.plusMinutes(30));
        b.setPlannedEnd(end.plusMinutes(30));
        b.setLocation(locB);

        when(changeRequestRepository.findByOrgIdAndStatusIn(orgId, List.of(ChangeRequest.Status.SCHEDULED, ChangeRequest.Status.IN_PROGRESS)))
                .thenReturn(List.of(a, b));

        var response = changeService.getCalendar(orgId, start.minusHours(1), end.plusHours(2));

        assertTrue(response.conflicts().isEmpty());
    }

    @Test
    void calendarDoesNotFlagOverlapWhenLocationMissing() {
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusHours(2);

        ChangeRequest a = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.SCHEDULED, null);
        a.setPlannedStart(start);
        a.setPlannedEnd(end);

        ChangeRequest b = changeWithTypeAndStatus(ChangeRequest.ChangeType.NORMAL, ChangeRequest.Status.SCHEDULED, null);
        b.setPlannedStart(start.plusMinutes(30));
        b.setPlannedEnd(end.plusMinutes(30));

        when(changeRequestRepository.findByOrgIdAndStatusIn(orgId, List.of(ChangeRequest.Status.SCHEDULED, ChangeRequest.Status.IN_PROGRESS)))
                .thenReturn(List.of(a, b));

        var response = changeService.getCalendar(orgId, start.minusHours(1), end.plusHours(2));

        assertTrue(response.conflicts().isEmpty());
    }
}
