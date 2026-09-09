package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.location.LocationRequest;
import com.alignedcardio.itsm.api.location.LocationResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Location;
import com.alignedcardio.itsm.event.LocationApprovalManagerChangedEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.repository.ServiceRequestRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final AppUserRepository appUserRepository;
    private final IncidentRepository incidentRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LocationService(LocationRepository locationRepository,
                           AppUserRepository appUserRepository,
                           IncidentRepository incidentRepository,
                           ServiceRequestRepository serviceRequestRepository,
                           ApplicationEventPublisher eventPublisher) {
        this.locationRepository = locationRepository;
        this.appUserRepository = appUserRepository;
        this.incidentRepository = incidentRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> list(UUID orgId) {
        return locationRepository.findByOrgIdAndDeletedAtIsNullOrderByName(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LocationResponse create(AppUser user, UUID orgId, LocationRequest request) {
        Location location = new Location();
        location.setOrgId(orgId);
        apply(location, request, orgId);
        location.setCreatedBy(user.getId());
        location.setUpdatedBy(user.getId());
        Location saved = locationRepository.save(location);
        publishManagerChangeIfNeeded(saved, null);
        return toResponse(saved);
    }

    @Transactional
    public LocationResponse update(AppUser user, UUID orgId, UUID id, LocationRequest request) {
        Location location = locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Location not found"));
        UUID previousManagerId = location.getApprovalManager() != null
                ? location.getApprovalManager().getId() : null;
        apply(location, request, orgId);
        location.setUpdatedBy(user.getId());
        location.setUpdatedAt(OffsetDateTime.now());
        Location saved = locationRepository.save(location);
        publishManagerChangeIfNeeded(saved, previousManagerId);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Location location = locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Location not found"));
        if (incidentRepository.existsByLocation_Id(id) || serviceRequestRepository.existsByLocation_Id(id)) {
            throw new IllegalStateException("Location is in use and cannot be deleted");
        }
        location.softDelete();
        locationRepository.save(location);
    }

    private void apply(Location location, LocationRequest request, UUID orgId) {
        location.setName(request.name().trim());
        location.setAddress(request.address());
        if (request.approvalManagerUserId() != null) {
            AppUser manager = appUserRepository.findByOrgIdAndId(orgId, request.approvalManagerUserId())
                    .orElseThrow(() -> new NotFoundException("Approval manager not found"));
            location.setApprovalManager(manager);
        } else {
            location.setApprovalManager(null);
        }
    }

    private void publishManagerChangeIfNeeded(Location location, UUID previousManagerId) {
        UUID newManagerId = location.getApprovalManager() != null
                ? location.getApprovalManager().getId() : null;
        if (Objects.equals(previousManagerId, newManagerId)) {
            return; // no-op save - manager unchanged
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("locationId", location.getId());
        payload.put("locationName", location.getName());
        if (newManagerId != null) {
            payload.put("newManagerId", newManagerId);
        }
        if (previousManagerId != null) {
            payload.put("oldManagerId", previousManagerId);
        }

        String triggerType = newManagerId != null
                ? "APPROVAL_MANAGER_ASSIGNED"
                : "APPROVAL_MANAGER_REMOVED";
        eventPublisher.publishEvent(new LocationApprovalManagerChangedEvent(
                location.getOrgId(), location.getId(), triggerType, payload));
    }

    private LocationResponse toResponse(Location location) {
        AppUser manager = location.getApprovalManager();
        return new LocationResponse(
                location.getId(),
                location.getName(),
                location.getAddress(),
                manager != null ? manager.getId() : null,
                manager != null ? manager.getDisplayName() : null,
                location.getCreatedAt(),
                location.getUpdatedAt());
    }
}
