package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.servicerequest.ApprovalRequest;
import com.alignedcardio.itsm.api.servicerequest.ServiceRequestActivityResponse;
import com.alignedcardio.itsm.api.servicerequest.ServiceRequestResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Location;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.event.ServiceRequestEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.CatalogItemRepository;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.repository.ServiceRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceRequestServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    @Mock private ServiceRequestRepository serviceRequestRepository;
    @Mock private CatalogItemRepository catalogItemRepository;
    @Mock private FulfillmentTaskRepository fulfillmentTaskRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private com.alignedcardio.itsm.repository.TeamMemberRepository teamMemberRepository;
    @Mock private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ServiceRequestService service;

    @BeforeEach
    void setUp() {
        service = new ServiceRequestService(
                serviceRequestRepository,
                catalogItemRepository,
                fulfillmentTaskRepository,
                appUserRepository,
                locationRepository,
                new FormSchemaValidator(objectMapper),
                objectMapper,
                entityManager,
                auditLogRepository,
                eventPublisher,
                teamMemberRepository);
    }

    private AppUser user(String name) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setOrgId(ORG_ID);
        u.setDisplayName(name);
        return u;
    }

    private CatalogItem item(String name, boolean approvalRequired, AppUser approver) {
        CatalogItem item = new CatalogItem();
        item.setId(UUID.randomUUID());
        item.setOrgId(ORG_ID);
        item.setName(name);
        item.setApprovalRequired(approvalRequired);
        item.setApprover(approver);
        item.setFormSchema(objectMapper.createArrayNode());
        return item;
    }

    private ServiceRequest pendingRequest(CatalogItem item, AppUser requester, Location location) {
        ServiceRequest sr = new ServiceRequest();
        sr.setId(UUID.randomUUID());
        sr.setOrgId(ORG_ID);
        sr.setNumber("SR-1");
        sr.setCatalogItem(item);
        sr.setRequester(requester);
        sr.setLocation(location);
        sr.setApprovalRequired(item.isApprovalRequired());
        sr.setStatus(ServiceRequest.Status.SUBMITTED);
        sr.setFormData(objectMapper.createObjectNode());
        return sr;
    }

    private void stubSave() {
        when(serviceRequestRepository.save(any(ServiceRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(any()))
                .thenReturn(List.of());
    }

    @Test
    void submitRoutesToLocationApprovalManager() {
        AppUser requester = user("Requester");
        AppUser locationManager = user("Location Manager");
        AppUser catalogApprover = user("Catalog Approver");
        CatalogItem item = item("Clinical Software", true, catalogApprover);
        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setApprovalManager(locationManager);

        ServiceRequest sr = pendingRequest(item, requester, location);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();

        ServiceRequestResponse response = service.submit(requester, ORG_ID, sr.getId());

        assertEquals(ServiceRequest.Status.PENDING_APPROVAL, response.status());
        assertEquals(locationManager.getId(), sr.getApprover().getId());
    }

    @Test
    void submitFallsBackToCatalogApproverWhenLocationHasNoManager() {
        AppUser requester = user("Requester");
        AppUser catalogApprover = user("Catalog Approver");
        CatalogItem item = item("Clinical Software", true, catalogApprover);
        Location location = new Location();
        location.setId(UUID.randomUUID());

        ServiceRequest sr = pendingRequest(item, requester, location);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();

        service.submit(requester, ORG_ID, sr.getId());

        assertEquals(catalogApprover.getId(), sr.getApprover().getId());
    }

    @Test
    void submitBlocksWhenNoApproverResolvableAndNamesItem() {
        AppUser requester = user("Requester");
        CatalogItem item = item("Clinical Software", true, null);

        ServiceRequest sr = pendingRequest(item, requester, null);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.submit(requester, ORG_ID, sr.getId()));
        assertTrue(e.getMessage().contains("Clinical Software"));
        assertTrue(e.getMessage().contains("no approver"));
    }

    @Test
    void decideAllowedForLocationManagerNotCatalogApprover() {
        AppUser requester = user("Requester");
        AppUser locationManager = user("Location Manager");
        AppUser catalogApprover = user("Catalog Approver");
        CatalogItem item = item("Clinical Software", true, catalogApprover);
        Location location = new Location();
        location.setApprovalManager(locationManager);

        ServiceRequest sr = pendingRequest(item, requester, location);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(locationManager); // resolved at submit time
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();

        // The catalog item's approver must NOT be able to decide a location-routed request
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.decide(catalogApprover, ORG_ID, sr.getId(), new ApprovalRequest("ok", true)));
        assertTrue(e.getMessage().contains("not the designated approver"));

        // The location's approval manager CAN decide
        ServiceRequestResponse response = service.decide(locationManager, ORG_ID, sr.getId(),
                new ApprovalRequest("looks good", true));
        assertEquals(ServiceRequest.Status.APPROVED, response.status());
    }

    @Test
    void endUserApprovalManagerSeesAndDecidesOwnQueue() {
        // Regression: an END_USER (no staff roles) who is a location approval
        // manager must see the request in their approvals queue and be able to
        // decide it. Backend authorization is service-level (approver match),
        // not role-based.
        AppUser requester = user("Requester");
        AppUser endUserManager = user("End User Manager");
        CatalogItem item = item("Clinical Software", true, null);
        Location location = new Location();
        location.setApprovalManager(endUserManager);

        ServiceRequest sr = pendingRequest(item, requester, location);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(endUserManager);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        when(serviceRequestRepository.findByOrgIdAndStatusAndApprover_IdOrderByCreatedAtDesc(
                ORG_ID, ServiceRequest.Status.PENDING_APPROVAL, endUserManager.getId()))
                .thenReturn(List.of(sr));
        stubSave();

        List<ServiceRequestResponse> queue = service.listPendingApprovals(endUserManager);
        assertEquals(1, queue.size());
        assertEquals(sr.getId(), queue.get(0).id());

        ServiceRequestResponse response = service.decide(endUserManager, ORG_ID, sr.getId(),
                new ApprovalRequest("approved", true));
        assertEquals(ServiceRequest.Status.APPROVED, response.status());
    }

    @Test
    void rejectRequiresReason() {
        AppUser requester = user("Requester");
        AppUser approver = user("Approver");
        CatalogItem item = item("Clinical Software", true, approver);

        ServiceRequest sr = pendingRequest(item, requester, null);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(approver);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.decide(approver, ORG_ID, sr.getId(), new ApprovalRequest("  ", false)));
        assertTrue(e.getMessage().contains("comment is required"));
    }

    @Test
    void approveRequiresComment() {
        AppUser requester = user("Requester");
        AppUser approver = user("Approver");
        CatalogItem item = item("Clinical Software", true, approver);

        ServiceRequest sr = pendingRequest(item, requester, null);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(approver);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.decide(approver, ORG_ID, sr.getId(), new ApprovalRequest(null, true)));
        assertTrue(e.getMessage().contains("comment is required"));
    }

    @Test
    void rejectWithReasonSucceedsAndPublishesEvent() {
        AppUser requester = user("Requester");
        AppUser approver = user("Approver");
        CatalogItem item = item("Clinical Software", true, approver);

        ServiceRequest sr = pendingRequest(item, requester, null);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(approver);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();

        ServiceRequestResponse response = service.decide(approver, ORG_ID, sr.getId(),
                new ApprovalRequest("Not needed this quarter", false));

        assertEquals(ServiceRequest.Status.REJECTED, response.status());
        assertEquals("Not needed this quarter", response.approvalComment());

        ArgumentCaptor<ServiceRequestEvent> captor = ArgumentCaptor.forClass(ServiceRequestEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ServiceRequestEvent event = captor.getValue();
        assertEquals("REJECTED", event.triggerType());
        assertEquals("SERVICE_REQUEST", event.triggerEntity());
        assertEquals(requester.getId(), event.payload().get("requesterId"));
        assertEquals("Not needed this quarter", event.payload().get("reason"));
    }

    @Test
    void approvePublishesApprovedAndInFulfillmentEvents() {
        AppUser requester = user("Requester");
        AppUser approver = user("Approver");
        CatalogItem item = item("Clinical Software", true, approver);

        ServiceRequest sr = pendingRequest(item, requester, null);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(approver);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();

        service.decide(approver, ORG_ID, sr.getId(), new ApprovalRequest("approved", true));

        ArgumentCaptor<ServiceRequestEvent> captor = ArgumentCaptor.forClass(ServiceRequestEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        List<String> types = captor.getAllValues().stream().map(ServiceRequestEvent::triggerType).toList();
        assertEquals(List.of("APPROVED", "IN_FULFILLMENT"), types);
    }

    @Test
    void submitRecordsSubmittedAndRoutedActivity() {
        AppUser requester = user("Requester");
        AppUser approver = user("Approver");
        CatalogItem item = item("Clinical Software", true, approver);

        ServiceRequest sr = pendingRequest(item, requester, null);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();

        service.submit(requester, ORG_ID, sr.getId());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(2)).save(captor.capture());
        List<String> actions = captor.getAllValues().stream().map(AuditLog::getAction).toList();
        assertEquals(List.of("SUBMITTED", "ROUTED_TO_APPROVER"), actions);
        captor.getAllValues().forEach(log -> {
            assertEquals("SERVICE_REQUEST", log.getEntityType());
            assertEquals(sr.getId(), log.getEntityId());
            assertEquals(ORG_ID, log.getOrgId());
        });
    }

    @Test
    void getActivityReturnsTimeline() {
        AppUser requester = user("Requester");
        CatalogItem item = item("Clinical Software", true, null);
        ServiceRequest sr = pendingRequest(item, requester, null);

        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setOrgId(ORG_ID);
        log.setAction("SUBMITTED");
        log.setEntityType("SERVICE_REQUEST");
        log.setEntityId(sr.getId());
        log.setActorUserId(requester.getId());

        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        when(auditLogRepository.findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
                ORG_ID, "SERVICE_REQUEST", sr.getId())).thenReturn(List.of(log));
        when(appUserRepository.findById(requester.getId())).thenReturn(Optional.of(requester));

        List<ServiceRequestActivityResponse> activity = service.getActivity(requester, ORG_ID, sr.getId());

        assertEquals(1, activity.size());
        assertEquals("SUBMITTED", activity.get(0).action());
        assertEquals("Requester", activity.get(0).actorName());
    }

    @Test
    void getAllowsRequester() {
        AppUser requester = user("Requester");
        CatalogItem item = item("Clinical Software", false, null);
        ServiceRequest sr = pendingRequest(item, requester, null);

        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        when(fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(any()))
                .thenReturn(List.of());

        ServiceRequestResponse response = service.get(requester, ORG_ID, sr.getId());
        assertEquals(sr.getId(), response.id());
    }

    @Test
    void getRejectsUnrelatedEndUser() {
        AppUser requester = user("Requester");
        AppUser stranger = user("Stranger");
        CatalogItem item = item("Clinical Software", false, null);
        ServiceRequest sr = pendingRequest(item, requester, null);

        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));

        assertThrows(NotFoundException.class, () -> service.get(stranger, ORG_ID, sr.getId()));
    }

    @Test
    void getAllowsStaff() {
        AppUser requester = user("Requester");
        AppUser agent = user("Agent");
        Role agentRole = new Role();
        agentRole.setName("AGENT");
        UserRole ur = new UserRole();
        ur.setUser(agent);
        ur.setRole(agentRole);
        agent.getUserRoles().add(ur);

        CatalogItem item = item("Clinical Software", false, null);
        ServiceRequest sr = pendingRequest(item, requester, null);

        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        when(fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(any()))
                .thenReturn(List.of());

        ServiceRequestResponse response = service.get(agent, ORG_ID, sr.getId());
        assertEquals(sr.getId(), response.id());
    }

    @Test
    void listPendingApprovalsReturnsOnlyMyQueue() {
        AppUser approver = user("Approver");
        CatalogItem item = item("Clinical Software", true, approver);
        ServiceRequest sr = pendingRequest(item, user("Requester"), null);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(approver);

        when(serviceRequestRepository.findByOrgIdAndStatusAndApprover_IdOrderByCreatedAtDesc(
                ORG_ID, ServiceRequest.Status.PENDING_APPROVAL, approver.getId()))
                .thenReturn(List.of(sr));
        when(fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(any()))
                .thenReturn(List.of());

        List<ServiceRequestResponse> queue = service.listPendingApprovals(approver);

        assertEquals(1, queue.size());
        assertEquals(sr.getId(), queue.get(0).id());
    }

    private AppUser superAdmin() {
        AppUser admin = user("Super Admin");
        Role role = new Role();
        role.setName("SUPER_ADMIN");
        UserRole ur = new UserRole();
        ur.setUser(admin);
        ur.setRole(role);
        admin.getUserRoles().add(ur);
        return admin;
    }

    private FulfillmentTask pendingTask(ServiceRequest sr) {
        FulfillmentTask task = new FulfillmentTask();
        task.setId(UUID.randomUUID());
        task.setOrgId(ORG_ID);
        task.setServiceRequest(sr);
        task.setDescription("Deliver laptop");
        task.setStatus(FulfillmentTask.Status.PENDING);
        return task;
    }

    @Test
    void assignTaskRequiresSuperAdmin() {
        AppUser requester = user("Requester");
        AppUser agent = user("Agent");
        Role agentRole = new Role();
        agentRole.setName("ADMIN"); // even ADMIN is not enough — SUPER_ADMIN only
        UserRole ur = new UserRole();
        ur.setUser(agent);
        ur.setRole(agentRole);
        agent.getUserRoles().add(ur);

        CatalogItem item = item("Laptop", false, null);
        ServiceRequest sr = pendingRequest(item, requester, null);
        FulfillmentTask task = pendingTask(sr);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.assignTask(agent, ORG_ID, sr.getId(), task.getId(), UUID.randomUUID()));
        assertTrue(e.getMessage().contains("SUPER_ADMIN"));
    }

    @Test
    void assignTaskRejectsNonTeamMember() {
        AppUser admin = superAdmin();
        AppUser outsider = user("Not In Team");
        CatalogItem item = item("Laptop", false, null);
        ServiceRequest sr = pendingRequest(item, user("Requester"), null);
        FulfillmentTask task = pendingTask(sr);

        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        when(fulfillmentTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(teamMemberRepository.findByTeamId(any())).thenReturn(List.of());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.assignTask(admin, ORG_ID, sr.getId(), task.getId(), outsider.getId()));
        assertTrue(e.getMessage().contains("IT Fulfillment team"));
    }

    @Test
    void assignTaskStaysPendingAndPublishesEvent() {
        AppUser admin = superAdmin();
        AppUser fulfiller = user("Fulfiller");
        CatalogItem item = item("Laptop", false, null);
        ServiceRequest sr = pendingRequest(item, user("Requester"), null);
        FulfillmentTask task = pendingTask(sr);

        com.alignedcardio.itsm.entity.TeamMember tm = new com.alignedcardio.itsm.entity.TeamMember();
        tm.setUser(fulfiller);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        when(fulfillmentTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(teamMemberRepository.findByTeamId(any())).thenReturn(List.of(tm));
        when(appUserRepository.findById(fulfiller.getId())).thenReturn(Optional.of(fulfiller));
        when(fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(any()))
                .thenReturn(List.of(task));

        service.assignTask(admin, ORG_ID, sr.getId(), task.getId(), fulfiller.getId());

        // Assignment no longer auto-advances: the fulfiller marks ORDERED explicitly.
        assertEquals(FulfillmentTask.Status.PENDING, task.getStatus());
        assertEquals(fulfiller.getId(), task.getAssignee().getId());
        assertEquals(admin.getId(), task.getAssignedBy().getId());
        assertNotNull(task.getAssignedAt());
        verify(eventPublisher).publishEvent(argThat((Object ev) ->
                ev instanceof ServiceRequestEvent sre
                        && "TASK_ASSIGNED".equals(sre.triggerType())));
    }

    private FulfillmentTask assignedTask(ServiceRequest sr, AppUser fulfiller) {
        FulfillmentTask task = pendingTask(sr);
        task.setAssignee(fulfiller);
        return task;
    }

    private void stubTaskLookup(ServiceRequest sr, FulfillmentTask task) {
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        when(fulfillmentTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
    }

    private void stubTaskList(FulfillmentTask task) {
        when(fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(any()))
                .thenReturn(List.of(task));
    }

    @Test
    void markOrderedTransitionsPendingAssignedTask() {
        AppUser fulfiller = user("Fulfiller");
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), user("Requester"), null);
        FulfillmentTask task = assignedTask(sr, fulfiller);
        stubTaskLookup(sr, task);
        stubTaskList(task);

        service.markOrdered(fulfiller, ORG_ID, sr.getId(), task.getId());

        assertEquals(FulfillmentTask.Status.ORDERED, task.getStatus());
    }

    @Test
    void markOrderedRequiresAssignee() {
        AppUser admin = superAdmin();
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), user("Requester"), null);
        FulfillmentTask task = pendingTask(sr); // no assignee
        stubTaskLookup(sr, task);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.markOrdered(admin, ORG_ID, sr.getId(), task.getId()));
        assertTrue(e.getMessage().contains("Assign a fulfiller"));
    }

    @Test
    void setDeliveryDateRequiresOrdered() {
        AppUser fulfiller = user("Fulfiller");
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), user("Requester"), null);
        FulfillmentTask task = assignedTask(sr, fulfiller); // still PENDING
        stubTaskLookup(sr, task);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.setDeliveryDate(fulfiller, ORG_ID, sr.getId(), task.getId(),
                        java.time.LocalDate.now().plusDays(3)));
        assertTrue(e.getMessage().contains("ORDERED"));
    }

    @Test
    void setDeliveryDatePublishesAssigneeAndLocation() {
        AppUser fulfiller = user("Fulfiller");
        Location location = new Location();
        location.setName("Main Clinic");
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), user("Requester"), location);
        FulfillmentTask task = assignedTask(sr, fulfiller);
        task.setStatus(FulfillmentTask.Status.ORDERED);
        stubTaskLookup(sr, task);
        stubTaskList(task);

        service.setDeliveryDate(fulfiller, ORG_ID, sr.getId(), task.getId(),
                java.time.LocalDate.of(2026, 9, 15));

        assertEquals(FulfillmentTask.Status.DELIVERY_DATE_SET, task.getStatus());
        verify(eventPublisher).publishEvent(argThat((Object ev) ->
                ev instanceof ServiceRequestEvent sre
                        && "DELIVERY_DATE_SET".equals(sre.triggerType())
                        && fulfiller.getId().equals(sre.payload().get("assigneeId"))
                        && "Main Clinic".equals(sre.payload().get("locationName"))));
    }

    @Test
    void markDeliveredRequiresDeliveryDateSet() {
        AppUser fulfiller = user("Fulfiller");
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), user("Requester"), null);
        FulfillmentTask task = assignedTask(sr, fulfiller);
        task.setStatus(FulfillmentTask.Status.ORDERED); // skipped delivery date
        stubTaskLookup(sr, task);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.markDelivered(fulfiller, ORG_ID, sr.getId(), task.getId()));
        assertTrue(e.getMessage().contains("delivery date"));
    }

    @Test
    void completeTaskRequiresDeliveredAndClosingNotes() {
        AppUser fulfiller = user("Fulfiller");
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), user("Requester"), null);
        FulfillmentTask task = assignedTask(sr, fulfiller);
        task.setStatus(FulfillmentTask.Status.DELIVERY_DATE_SET); // not yet installed
        stubTaskLookup(sr, task);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.completeTask(fulfiller, ORG_ID, sr.getId(), task.getId(), "done"));
        assertTrue(e.getMessage().contains("installed"));

        task.setStatus(FulfillmentTask.Status.DELIVERED);
        e = assertThrows(IllegalStateException.class,
                () -> service.completeTask(fulfiller, ORG_ID, sr.getId(), task.getId(), "  "));
        assertTrue(e.getMessage().contains("Closing notes"));
    }

    @Test
    void completeTaskStoresClosingNotesAndFulfillsRequest() {
        AppUser fulfiller = user("Fulfiller");
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), user("Requester"), null);
        sr.setStatus(ServiceRequest.Status.IN_FULFILLMENT);
        FulfillmentTask task = assignedTask(sr, fulfiller);
        task.setStatus(FulfillmentTask.Status.DELIVERED);
        stubSave();
        stubTaskLookup(sr, task);
        stubTaskList(task); // must come after stubSave so the task list wins

        service.completeTask(fulfiller, ORG_ID, sr.getId(), task.getId(), "Installed and verified");

        assertEquals(FulfillmentTask.Status.COMPLETED, task.getStatus());
        assertEquals("Installed and verified", task.getClosingNotes());
        assertNotNull(task.getCompletedAt());
        assertEquals(ServiceRequest.Status.FULFILLED, sr.getStatus());
        verify(eventPublisher).publishEvent(argThat((Object ev) ->
                ev instanceof ServiceRequestEvent sre
                        && "FULFILLED".equals(sre.triggerType())));
    }
}
