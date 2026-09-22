package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.servicerequest.ApprovalRequest;
import com.alignedcardio.itsm.api.servicerequest.ServiceRequestActivityResponse;
import com.alignedcardio.itsm.api.servicerequest.ServiceRequestCreateRequest;
import com.alignedcardio.itsm.api.servicerequest.ServiceRequestResponse;
import com.alignedcardio.itsm.api.servicerequest.ServiceRequestUpdateRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @Mock private NotificationService notificationService;
    @Mock private NotificationTemplateBuilder notificationTemplateBuilder;
    @Mock private SlaEngine slaEngine;
    @Mock private com.alignedcardio.itsm.repository.PriorityRepository priorityRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<UUID, ServiceRequest> savedRequests = new HashMap<>();
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
                teamMemberRepository,
                notificationService,
                notificationTemplateBuilder,
                slaEngine,
                priorityRepository);
    }

    private AppUser user(String name) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setOrgId(ORG_ID);
        u.setDisplayName(name);
        return u;
    }

    private CatalogItem item(String name, boolean requiresApproval, AppUser approver) {
        CatalogItem item = new CatalogItem();
        item.setId(UUID.randomUUID());
        item.setOrgId(ORG_ID);
        item.setName(name);
        item.setApprover(approver);

        ObjectNode option = objectMapper.createObjectNode();
        option.put("value", "Standard");
        option.put("label", "Standard");
        option.put("requiresApproval", requiresApproval);

        ArrayNode options = objectMapper.createArrayNode().add(option);

        ObjectNode field = objectMapper.createObjectNode();
        field.put("name", "request");
        field.put("label", "Request");
        field.put("type", "select");
        field.set("options", options);

        item.setFormSchema(objectMapper.createArrayNode().add(field));
        return item;
    }

    private boolean requiresApprovalFor(CatalogItem item) {
        if (item.getFormSchema() == null || !item.getFormSchema().isArray()) {
            return false;
        }
        for (JsonNode field : item.getFormSchema()) {
            JsonNode options = field.get("options");
            if (options == null || !options.isArray()) {
                continue;
            }
            for (JsonNode opt : options) {
                if (opt.isObject() && "Standard".equals(opt.get("value").asText())) {
                    return opt.has("requiresApproval") && opt.get("requiresApproval").asBoolean();
                }
            }
        }
        return false;
    }

    private ServiceRequest pendingRequest(CatalogItem item, AppUser requester, Location location) {
        ServiceRequest sr = new ServiceRequest();
        sr.setId(UUID.randomUUID());
        sr.setOrgId(ORG_ID);
        sr.setNumber("SR-1");
        sr.setCatalogItem(item);
        sr.setRequester(requester);
        sr.setLocation(location);
        sr.setFormData(objectMapper.createObjectNode().put("request", "Standard"));
        sr.setApprovalRequired(requiresApprovalFor(item));
        sr.setStatus(ServiceRequest.Status.SUBMITTED);
        return sr;
    }

    private void stubSave() {
        when(serviceRequestRepository.save(any(ServiceRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(any()))
                .thenReturn(List.of());
    }

    private void stubCreate() {
        savedRequests.clear();
        Query numberQuery = mock(Query.class);
        when(numberQuery.getSingleResult()).thenReturn(1L);
        when(entityManager.createNativeQuery("SELECT nextval('service_request_number_seq')")).thenReturn(numberQuery);

        when(serviceRequestRepository.save(any(ServiceRequest.class)))
                .thenAnswer(inv -> {
                    ServiceRequest r = inv.getArgument(0);
                    if (r.getId() == null) r.setId(UUID.randomUUID());
                    savedRequests.put(r.getId(), r);
                    return r;
                });
        when(fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(any()))
                .thenReturn(List.of());
        when(serviceRequestRepository.findByOrgIdAndId(eq(ORG_ID), any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(savedRequests.get(inv.getArgument(1))));
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

    private AppUser superAdmin(String name) {
        AppUser admin = user(name);
        Role role = new Role();
        role.setName("SUPER_ADMIN");
        UserRole ur = new UserRole();
        ur.setUser(admin);
        ur.setRole(role);
        admin.setUserRoles(java.util.Set.of(ur));
        return admin;
    }

    private Location locationWithManager(String name, AppUser manager) {
        Location loc = new Location();
        loc.setId(UUID.randomUUID());
        loc.setName(name);
        loc.setApprovalManager(manager);
        return loc;
    }

    @Test
    void updateLogsOnlyChangedFields() {
        AppUser admin = superAdmin("Admin");
        AppUser requester = user("Requester");
        CatalogItem item = item("Clinical Software", true, null);
        ServiceRequest sr = pendingRequest(item, requester, null);
        sr.setStatus(ServiceRequest.Status.IN_FULFILLMENT);
        sr.setPhone("555-000-1111");
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();

        service.update(admin, ORG_ID, sr.getId(),
                new ServiceRequestUpdateRequest(null, null, "555-222-3333", null, null));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("EDITED", log.getAction());
        assertEquals(admin.getId(), log.getActorUserId());
        // Only the changed field appears in the diff — nothing else.
        assertTrue(log.getAfterState().contains("phone"));
        assertFalse(log.getAfterState().contains("location"));
        assertFalse(log.getAfterState().contains("priority"));
        assertFalse(log.getAfterState().contains("formData"));
        assertTrue(log.getBeforeState().contains("555-000-1111"));
    }

    @Test
    void updateLocationWhilePendingReresolvesApprover() {
        AppUser admin = superAdmin("Admin");
        AppUser requester = user("Requester");
        AppUser oldManager = user("Old Manager");
        AppUser newManager = user("New Manager");
        CatalogItem item = item("Clinical Software", true, null);
        Location oldLoc = locationWithManager("Old Site", oldManager);
        Location newLoc = locationWithManager("New Site", newManager);

        ServiceRequest sr = pendingRequest(item, requester, oldLoc);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(oldManager);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, newLoc.getId()))
                .thenReturn(Optional.of(newLoc));
        stubSave();

        service.update(admin, ORG_ID, sr.getId(),
                new ServiceRequestUpdateRequest(newLoc.getId(), null, null, null, null));

        assertEquals(newManager.getId(), sr.getApprover().getId());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        String after = captor.getValue().getAfterState();
        assertTrue(after.contains("New Site"));
        assertTrue(after.contains("New Manager"));
    }

    @Test
    void updateLocationAfterDecisionKeepsApprover() {
        AppUser admin = superAdmin("Admin");
        AppUser requester = user("Requester");
        AppUser decider = user("Deciding Manager");
        AppUser newManager = user("New Manager");
        CatalogItem item = item("Clinical Software", true, null);
        Location oldLoc = locationWithManager("Old Site", decider);
        Location newLoc = locationWithManager("New Site", newManager);

        ServiceRequest sr = pendingRequest(item, requester, oldLoc);
        sr.setStatus(ServiceRequest.Status.IN_FULFILLMENT);
        sr.setApprover(decider);
        sr.setApprovalDecision(ServiceRequest.ApprovalDecision.APPROVED);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, newLoc.getId()))
                .thenReturn(Optional.of(newLoc));
        stubSave();

        service.update(admin, ORG_ID, sr.getId(),
                new ServiceRequestUpdateRequest(newLoc.getId(), null, null, null, null));

        // A made decision never re-routes: the approver stays the decider.
        assertEquals(decider.getId(), sr.getApprover().getId());
        assertEquals(newLoc.getId(), sr.getLocation().getId());
    }

    @Test
    void updateRejectsNonSuperAdmin() {
        AppUser requester = user("Requester");
        CatalogItem item = item("Clinical Software", false, null);
        ServiceRequest sr = pendingRequest(item, requester, null);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.update(requester, ORG_ID, sr.getId(),
                        new ServiceRequestUpdateRequest(null, null, "555", null, null)));
        assertTrue(e.getMessage().contains("SUPER_ADMIN"));
    }

    @Test
    void bypassApprovalMarksDistinctAndNotifiesApprover() {
        AppUser admin = superAdmin("Super Admin");
        AppUser requester = user("Requester");
        AppUser manager = user("Approval Manager");
        CatalogItem item = item("Clinical Software", true, manager);
        Location loc = locationWithManager("Site", manager);

        ServiceRequest sr = pendingRequest(item, requester, loc);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(manager);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        when(notificationTemplateBuilder.forEvent(eq("SR_APPROVAL_BYPASSED"), any()))
                .thenReturn(new com.alignedcardio.itsm.service.notification.NotificationContent(
                        "s", "b", "h", "s", "i", "p", "p"));
        stubSave();

        ServiceRequestResponse response =
                service.bypassApproval(admin, ORG_ID, sr.getId(), "Urgent clinical need");

        assertEquals(ServiceRequest.Status.APPROVED, response.status());
        assertEquals(ServiceRequest.ApprovalDecision.APPROVED, sr.getApprovalDecision());
        assertTrue(sr.isApprovalBypassed());
        assertEquals(admin.getId(), sr.getBypassedBy().getId());
        // The designated approver stays on the record — the bypasser is separate.
        assertEquals(manager.getId(), sr.getApprover().getId());
        assertEquals(manager.getId(), response.approverId());
        assertEquals(admin.getDisplayName(), response.bypassedByName());

        // Audit: distinct APPROVAL_BYPASSED action naming both parties + reason.
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog log = auditCaptor.getValue();
        assertEquals("APPROVAL_BYPASSED", log.getAction());
        assertTrue(log.getAfterState().contains("Super Admin"));
        assertTrue(log.getAfterState().contains("Approval Manager"));
        assertTrue(log.getAfterState().contains("Urgent clinical need"));

        // Original approver is notified of the override.
        ArgumentCaptor<com.alignedcardio.itsm.service.notification.NotificationRequest> notifCaptor =
                ArgumentCaptor.forClass(com.alignedcardio.itsm.service.notification.NotificationRequest.class);
        verify(notificationService).send(notifCaptor.capture());
        assertEquals(manager.getId(), notifCaptor.getValue().userId());
        assertEquals("SR_APPROVAL_BYPASSED", notifCaptor.getValue().type());

        // Same downstream as a normal approval: requester APPROVED +
        // IN_FULFILLMENT events drive the requester notification.
        ArgumentCaptor<ServiceRequestEvent> eventCaptor = ArgumentCaptor.forClass(ServiceRequestEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        List<String> types = eventCaptor.getAllValues().stream().map(ServiceRequestEvent::triggerType).toList();
        assertEquals(List.of("APPROVED", "IN_FULFILLMENT"), types);
    }

    @Test
    void bypassApprovalRequiresSuperAdminReasonAndPending() {
        AppUser admin = superAdmin("Admin");
        AppUser manager = user("Manager");
        CatalogItem item = item("Clinical Software", true, manager);
        ServiceRequest sr = pendingRequest(item, user("Requester"), null);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(manager);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));

        assertThrows(IllegalStateException.class,
                () -> service.bypassApproval(manager, ORG_ID, sr.getId(), "reason"));
        assertThrows(IllegalStateException.class,
                () -> service.bypassApproval(admin, ORG_ID, sr.getId(), "  "));

        sr.setStatus(ServiceRequest.Status.IN_FULFILLMENT);
        assertThrows(IllegalStateException.class,
                () -> service.bypassApproval(admin, ORG_ID, sr.getId(), "reason"));
    }

    @Test
    void approvedByMeUsesBypassedExcludingQuery() {
        // listApprovedByMe must hit the repository method that filters out
        // approval_bypassed rows, so bypassed requests never appear in the
        // designated approver's "approved by me" history.
        AppUser manager = user("Manager");
        service.listApprovedByMe(manager);
        verify(serviceRequestRepository)
                .findByOrgIdAndApprover_IdAndApprovalDecisionAndApprovalBypassedFalseAndDeletedAtIsNullOrderByCreatedAtDesc(
                        ORG_ID, manager.getId(), ServiceRequest.ApprovalDecision.APPROVED);
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

    // --- Part K: held requests freeze fulfillment progression ---

    @Test
    void taskMutationsRejectedWhileRequestOnHold() {
        AppUser fulfiller = user("Fulfiller");
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), user("Requester"), null);
        sr.setStatus(ServiceRequest.Status.ON_HOLD);
        FulfillmentTask task = assignedTask(sr, fulfiller);
        task.setStatus(FulfillmentTask.Status.ORDERED);
        stubTaskLookup(sr, task);

        assertOnHold(() -> service.markOrdered(fulfiller, ORG_ID, sr.getId(), task.getId()));
        assertOnHold(() -> service.setDeliveryDate(fulfiller, ORG_ID, sr.getId(), task.getId(),
                java.time.LocalDate.now().plusDays(1)));
        assertOnHold(() -> service.markDelivered(fulfiller, ORG_ID, sr.getId(), task.getId()));
        assertOnHold(() -> service.completeTask(fulfiller, ORG_ID, sr.getId(), task.getId(), "done"));
    }

    private void assertOnHold(org.junit.jupiter.api.function.Executable action) {
        IllegalStateException e = assertThrows(IllegalStateException.class, action);
        assertTrue(e.getMessage().contains("on hold"));
    }

    // --- Part J: cancel ---

    @Test
    void requesterCanCancelOwnRequestWithReason() {
        AppUser requester = user("Requester");
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), requester, null);
        sr.setStatus(ServiceRequest.Status.IN_FULFILLMENT);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();

        ServiceRequestResponse response = service.cancel(requester, ORG_ID, sr.getId(), "No longer needed");

        assertEquals(ServiceRequest.Status.CANCELLED, response.status());
        verify(slaEngine).onServiceRequestStatusChanged(sr);
        var captor = org.mockito.ArgumentCaptor.forClass(com.alignedcardio.itsm.entity.AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals("CANCELLED", captor.getValue().getAction());
        assertTrue(captor.getValue().getAfterState().contains("No longer needed"));
    }

    @Test
    void cancelRequiresReasonAndRejectsOutsiders() {
        AppUser requester = user("Requester");
        AppUser outsider = user("Outsider"); // no staff role
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), requester, null);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));

        assertThrows(IllegalStateException.class,
                () -> service.cancel(requester, ORG_ID, sr.getId(), "   "));
        assertThrows(IllegalStateException.class,
                () -> service.cancel(outsider, ORG_ID, sr.getId(), "Try"));
        assertEquals(ServiceRequest.Status.SUBMITTED, sr.getStatus());
    }

    @Test
    void updateStatusRejectsCancelledWithoutReason() {
        ServiceRequest sr = pendingRequest(item("Laptop", false, null), user("Requester"), null);
        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.updateStatus(superAdmin(), ORG_ID, sr.getId(), ServiceRequest.Status.CANCELLED));
        assertTrue(e.getMessage().contains("reason"));
    }

    @Test
    void sameFieldDifferentOptionsRouteDifferently() {
        AppUser requester = user("Requester");
        AppUser catalogApprover = user("Catalog Approver");

        // One catalog item, one select field, two options with different approval flags
        CatalogItem item = new CatalogItem();
        item.setId(UUID.randomUUID());
        item.setOrgId(ORG_ID);
        item.setName("Printer");
        item.setApprover(catalogApprover);

        ObjectNode approved = objectMapper.createObjectNode();
        approved.put("value", "New printer (capital)");
        approved.put("label", "New printer (capital)");
        approved.put("requiresApproval", true);

        ObjectNode notApproved = objectMapper.createObjectNode();
        notApproved.put("value", "Toner / supplies");
        notApproved.put("label", "Toner / supplies");
        notApproved.put("requiresApproval", false);

        ArrayNode options = objectMapper.createArrayNode().add(approved).add(notApproved);

        ObjectNode field = objectMapper.createObjectNode();
        field.put("name", "request");
        field.put("label", "What do you need?");
        field.put("type", "select");
        field.set("options", options);

        item.setFormSchema(objectMapper.createArrayNode().add(field));

        when(catalogItemRepository.findByOrgIdAndId(ORG_ID, item.getId())).thenReturn(Optional.of(item));
        Location location = new Location();
        location.setId(UUID.randomUUID());
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, location.getId())).thenReturn(Optional.of(location));

        stubCreate();

        // Option requiring approval -> PENDING_APPROVAL
        ServiceRequestCreateRequest approvalReq = new ServiceRequestCreateRequest(
                item.getId(),
                objectMapper.createObjectNode().put("request", "New printer (capital)").toString(),
                null,
                location.getId(),
                "555-123-4567",
                null);
        ServiceRequestResponse approvalResponse = service.create(requester, ORG_ID, approvalReq);
        assertEquals(ServiceRequest.Status.PENDING_APPROVAL, approvalResponse.status());
        assertEquals(catalogApprover.getId(), approvalResponse.approverId());

        // Option NOT requiring approval -> IN_FULFILLMENT
        ServiceRequestCreateRequest noApprovalReq = new ServiceRequestCreateRequest(
                item.getId(),
                objectMapper.createObjectNode().put("request", "Toner / supplies").toString(),
                null,
                location.getId(),
                "555-123-4567",
                null);
        ServiceRequestResponse noApprovalResponse = service.create(requester, ORG_ID, noApprovalReq);
        assertEquals(ServiceRequest.Status.IN_FULFILLMENT, noApprovalResponse.status());
    }

    // Phone is mandatory on new submissions: exactly 10 US digits after
    // stripping formatting, stored normalized as plain digits.
    @Test
    void createRequiresValidTenDigitPhoneAndStoresNormalized() {
        AppUser requester = user("Requester");
        CatalogItem item = item("Laptop", false, null);
        Location location = new Location();
        location.setId(UUID.randomUUID());
        String formData = objectMapper.createObjectNode().put("request", "Standard").toString();

        when(catalogItemRepository.findByOrgIdAndId(ORG_ID, item.getId())).thenReturn(Optional.of(item));
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, location.getId()))
                .thenReturn(Optional.of(location));
        stubCreate();

        // Missing -> rejected with the clear message
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> service.create(requester, ORG_ID,
                        new ServiceRequestCreateRequest(item.getId(), formData, null, location.getId(), null, null)));
        assertEquals("Please enter a valid 10-digit phone number", missing.getMessage());

        // Wrong digit count -> rejected
        assertThrows(IllegalArgumentException.class,
                () -> service.create(requester, ORG_ID,
                        new ServiceRequestCreateRequest(item.getId(), formData, null, location.getId(), "555-1234", null)));

        // Formatted input -> accepted and stored as plain digits
        ServiceRequestResponse response = service.create(requester, ORG_ID,
                new ServiceRequestCreateRequest(item.getId(), formData, null, location.getId(), "(555) 123-4567", null));
        assertEquals("5551234567", response.phone());
    }

    @Test
    void sendToApprovalMidFulfillmentPreservesTaskState() {
        AppUser admin = superAdmin();
        AppUser approver = user("Approver");
        AppUser requester = user("Requester");
        CatalogItem item = item("Laptop", false, approver);
        ServiceRequest sr = pendingRequest(item, requester, null);
        sr.setStatus(ServiceRequest.Status.IN_FULFILLMENT);
        sr.setApprovalRequired(false);

        FulfillmentTask task = new FulfillmentTask();
        task.setId(UUID.randomUUID());
        task.setServiceRequest(sr);
        task.setDescription("Order laptop");
        task.setStatus(FulfillmentTask.Status.ORDERED);

        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();
        when(fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(sr.getId()))
                .thenReturn(List.of(task));

        ServiceRequestResponse response = service.sendToApproval(admin, ORG_ID, sr.getId(), "Budget needs sign-off");

        assertEquals(ServiceRequest.Status.PENDING_APPROVAL, response.status());
        assertEquals(ServiceRequest.Status.IN_FULFILLMENT, sr.getPreviousStatus());
        assertTrue(sr.isApprovalRequired());
        assertEquals(ServiceRequest.ApprovalDecision.PENDING, sr.getApprovalDecision());
        assertEquals(approver.getId(), sr.getApprover().getId());
        assertEquals(FulfillmentTask.Status.ORDERED, task.getStatus());
        assertEquals("Order laptop", task.getDescription());
    }

    @Test
    void approveResumesWithoutDuplicateTasks() {
        AppUser approver = user("Approver");
        AppUser requester = user("Requester");
        CatalogItem item = item("Laptop", false, approver);
        ServiceRequest sr = pendingRequest(item, requester, null);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprovalRequired(true);
        sr.setPreviousStatus(ServiceRequest.Status.IN_FULFILLMENT);
        sr.setApprover(approver);

        FulfillmentTask task = new FulfillmentTask();
        task.setId(UUID.randomUUID());
        task.setServiceRequest(sr);
        task.setDescription("Order laptop");
        task.setStatus(FulfillmentTask.Status.ORDERED);

        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();
        when(fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(sr.getId()))
                .thenReturn(List.of(task));

        ServiceRequestResponse response = service.decide(approver, ORG_ID, sr.getId(),
                new ApprovalRequest("Approved after review", true));

        assertEquals(ServiceRequest.Status.APPROVED, response.status());
        assertNull(sr.getPreviousStatus());
        assertEquals(FulfillmentTask.Status.ORDERED, task.getStatus());
        assertEquals("Order laptop", task.getDescription());
        verify(fulfillmentTaskRepository, never()).save(any(FulfillmentTask.class));
    }

    @Test
    void rejectionFromRetroactiveApprovalNeedsReviewAndNotifiesAdmins() {
        AppUser approver = user("Approver");
        AppUser admin = superAdmin();
        AppUser requester = user("Requester");
        CatalogItem item = item("Laptop", false, approver);
        ServiceRequest sr = pendingRequest(item, requester, null);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprovalRequired(true);
        sr.setPreviousStatus(ServiceRequest.Status.IN_FULFILLMENT);
        sr.setApprover(approver);

        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();
        when(appUserRepository.findByOrgIdAndRoleNames(ORG_ID, List.of("ADMIN", "SUPER_ADMIN")))
                .thenReturn(List.of(admin));
        com.alignedcardio.itsm.service.notification.NotificationContent content =
                new com.alignedcardio.itsm.service.notification.NotificationContent(
                        "subject", "body", "subject", "body", "push", "push");
        when(notificationTemplateBuilder.forEvent(eq("SR_RETROACTIVE_APPROVAL_REJECTED"), anyMap()))
                .thenReturn(content);

        ServiceRequestResponse response = service.decide(approver, ORG_ID, sr.getId(),
                new ApprovalRequest("Too expensive", false));

        assertEquals(ServiceRequest.Status.REJECTED_NEEDS_REVIEW, response.status());
        assertEquals(ServiceRequest.Status.IN_FULFILLMENT, sr.getPreviousStatus());
        assertEquals(ServiceRequest.ApprovalDecision.REJECTED, sr.getApprovalDecision());
        verify(notificationService, times(2)).send(any());
    }

    @Test
    void sendToApprovalActivityEntryContainsReason() {
        AppUser admin = superAdmin();
        AppUser approver = user("Approver");
        AppUser requester = user("Requester");
        CatalogItem item = item("Laptop", false, approver);
        ServiceRequest sr = pendingRequest(item, requester, null);
        sr.setStatus(ServiceRequest.Status.IN_FULFILLMENT);

        when(serviceRequestRepository.findByOrgIdAndId(ORG_ID, sr.getId())).thenReturn(Optional.of(sr));
        stubSave();

        service.sendToApproval(admin, ORG_ID, sr.getId(), "Manager must review vendor");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("SENT_TO_APPROVAL", log.getAction());
        assertNotNull(log.getAfterState());
        assertTrue(log.getAfterState().contains("Manager must review vendor"));
        assertTrue(log.getAfterState().contains(approver.getId().toString()));
    }
}
