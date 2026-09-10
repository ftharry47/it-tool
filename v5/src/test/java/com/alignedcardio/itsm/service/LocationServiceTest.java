package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.location.LocationRequest;
import com.alignedcardio.itsm.api.location.LocationResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.entity.Location;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.event.LocationApprovalManagerChangedEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.repository.ServiceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    private static final UUID ORG_ID = BaseEntity.DEFAULT_ORG_ID;

    @Mock private LocationRepository locationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private ServiceRequestRepository serviceRequestRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private LocationService locationService;

    @BeforeEach
    void setUp() {
        locationService = new LocationService(locationRepository, appUserRepository, incidentRepository,
                serviceRequestRepository, eventPublisher);
        lenient().when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createAssignsApprovalManagerWithAgentRole() {
        AppUser admin = userWithRole("ADMIN");
        AppUser manager = userWithRole("AGENT");
        when(appUserRepository.findByOrgIdAndId(ORG_ID, manager.getId())).thenReturn(Optional.of(manager));

        LocationResponse response = locationService.create(admin, ORG_ID,
                new LocationRequest("Richmond HQ", "123 Main St", manager.getId()));

        assertEquals("Richmond HQ", response.name());
        assertEquals(manager.getId(), response.approvalManagerUserId());
        verify(locationRepository).save(any(Location.class));
    }

    @Test
    void createAllowsEndUserAsApprovalManager() {
        AppUser admin = userWithRole("ADMIN");
        AppUser endUser = userWithRole("END_USER");
        when(appUserRepository.findByOrgIdAndId(ORG_ID, endUser.getId())).thenReturn(Optional.of(endUser));

        LocationResponse response = locationService.create(admin, ORG_ID,
                new LocationRequest("Richmond HQ", null, endUser.getId()));

        assertEquals(endUser.getId(), response.approvalManagerUserId());
        verify(locationRepository).save(any(Location.class));
    }

    @Test
    void updateRemovingManagerClearsApprovalManager() {
        // Boundary for the derived isApprovalManager flag: once the last
        // location stops referencing a user, existsByApprovalManager_IdAndDeletedAtIsNull
        // returns false and their Approvals access disappears.
        AppUser admin = userWithRole("ADMIN");
        AppUser oldManager = userWithRole("END_USER");
        Location location = location("Richmond HQ");
        location.setApprovalManager(oldManager);
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, location.getId()))
                .thenReturn(Optional.of(location));

        LocationResponse response = locationService.update(admin, ORG_ID, location.getId(),
                new LocationRequest("Richmond HQ", null, null));

        assertNull(location.getApprovalManager());
        assertNull(response.approvalManagerUserId());
    }

    @Test
    void deleteBlockedWhenLocationInUseByIncident() {
        Location location = location("Richmond HQ");
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, location.getId()))
                .thenReturn(Optional.of(location));
        when(incidentRepository.existsByLocation_Id(location.getId())).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> locationService.delete(ORG_ID, location.getId()));
        verify(locationRepository, never()).save(any(Location.class));
        assertNull(location.getDeletedAt());
    }

    @Test
    void deleteBlockedWhenLocationInUseByServiceRequest() {
        Location location = location("Richmond HQ");
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, location.getId()))
                .thenReturn(Optional.of(location));
        when(incidentRepository.existsByLocation_Id(location.getId())).thenReturn(false);
        when(serviceRequestRepository.existsByLocation_Id(location.getId())).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> locationService.delete(ORG_ID, location.getId()));
        verify(locationRepository, never()).save(any(Location.class));
    }

    @Test
    void deleteSoftDeletesWhenNotInUse() {
        Location location = location("Richmond HQ");
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, location.getId()))
                .thenReturn(Optional.of(location));
        when(incidentRepository.existsByLocation_Id(location.getId())).thenReturn(false);
        when(serviceRequestRepository.existsByLocation_Id(location.getId())).thenReturn(false);

        locationService.delete(ORG_ID, location.getId());

        assertNotNull(location.getDeletedAt());
        verify(locationRepository).save(location);
    }

    @Test
    void deleteThrowsNotFoundForMissingLocation() {
        UUID id = UUID.randomUUID();
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> locationService.delete(ORG_ID, id));
    }

    @Test
    void createWithManagerPublishesAssignedEvent() {
        AppUser admin = userWithRole("ADMIN");
        AppUser manager = userWithRole("AGENT");
        when(appUserRepository.findByOrgIdAndId(ORG_ID, manager.getId())).thenReturn(Optional.of(manager));

        locationService.create(admin, ORG_ID, new LocationRequest("Richmond HQ", null, manager.getId()));

        ArgumentCaptor<LocationApprovalManagerChangedEvent> captor =
                ArgumentCaptor.forClass(LocationApprovalManagerChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        LocationApprovalManagerChangedEvent event = captor.getValue();
        assertEquals("APPROVAL_MANAGER_ASSIGNED", event.triggerType());
        assertEquals("LOCATION", event.triggerEntity());
        assertEquals(ORG_ID, event.orgId());
        assertEquals(manager.getId(), event.payload().get("newManagerId"));
        assertEquals("Richmond HQ", event.payload().get("locationName"));
    }

    @Test
    void createWithoutManagerPublishesNoEvent() {
        AppUser admin = userWithRole("ADMIN");

        locationService.create(admin, ORG_ID, new LocationRequest("Richmond HQ", null, null));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateWithSameManagerPublishesNoEvent() {
        AppUser admin = userWithRole("ADMIN");
        AppUser manager = userWithRole("AGENT");
        Location location = location("Richmond HQ");
        location.setApprovalManager(manager);
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, location.getId()))
                .thenReturn(Optional.of(location));
        when(appUserRepository.findByOrgIdAndId(ORG_ID, manager.getId())).thenReturn(Optional.of(manager));

        // No-op save: same manager id submitted again
        locationService.update(admin, ORG_ID, location.getId(),
                new LocationRequest("Richmond HQ", "New address", manager.getId()));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateWithDifferentManagerPublishesAssignedEvent() {
        AppUser admin = userWithRole("ADMIN");
        AppUser oldManager = userWithRole("AGENT");
        AppUser newManager = userWithRole("TEAM_LEAD");
        Location location = location("Richmond HQ");
        location.setApprovalManager(oldManager);
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, location.getId()))
                .thenReturn(Optional.of(location));
        when(appUserRepository.findByOrgIdAndId(ORG_ID, newManager.getId())).thenReturn(Optional.of(newManager));

        locationService.update(admin, ORG_ID, location.getId(),
                new LocationRequest("Richmond HQ", null, newManager.getId()));

        ArgumentCaptor<LocationApprovalManagerChangedEvent> captor =
                ArgumentCaptor.forClass(LocationApprovalManagerChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        LocationApprovalManagerChangedEvent event = captor.getValue();
        assertEquals("APPROVAL_MANAGER_ASSIGNED", event.triggerType());
        assertEquals(newManager.getId(), event.payload().get("newManagerId"));
        assertEquals(oldManager.getId(), event.payload().get("oldManagerId"));
    }

    @Test
    void updateRemovingManagerPublishesRemovedEvent() {
        AppUser admin = userWithRole("ADMIN");
        AppUser oldManager = userWithRole("AGENT");
        Location location = location("Richmond HQ");
        location.setApprovalManager(oldManager);
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, location.getId()))
                .thenReturn(Optional.of(location));

        locationService.update(admin, ORG_ID, location.getId(),
                new LocationRequest("Richmond HQ", null, null));

        ArgumentCaptor<LocationApprovalManagerChangedEvent> captor =
                ArgumentCaptor.forClass(LocationApprovalManagerChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        LocationApprovalManagerChangedEvent event = captor.getValue();
        assertEquals("APPROVAL_MANAGER_REMOVED", event.triggerType());
        assertEquals(oldManager.getId(), event.payload().get("oldManagerId"));
        assertNull(event.payload().get("newManagerId"));
    }

    private AppUser userWithRole(String roleName) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setOrgId(ORG_ID);
        user.setDisplayName(roleName + " User");
        user.setEmail(roleName.toLowerCase() + "@example.com");

        Role role = new Role();
        role.setName(roleName);
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        user.setUserRoles(Set.of(userRole));
        return user;
    }

    private Location location(String name) {
        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setOrgId(ORG_ID);
        location.setName(name);
        return location;
    }
}
