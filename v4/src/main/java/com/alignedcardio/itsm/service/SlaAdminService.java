package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.admin.BusinessCalendarRequest;
import com.alignedcardio.itsm.api.admin.SlaPolicyRequest;
import com.alignedcardio.itsm.entity.BusinessCalendar;
import com.alignedcardio.itsm.entity.SlaPolicy;
import com.alignedcardio.itsm.repository.BusinessCalendarRepository;
import com.alignedcardio.itsm.repository.SlaPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SlaAdminService {

    private final SlaPolicyRepository slaPolicyRepository;
    private final BusinessCalendarRepository businessCalendarRepository;

    public SlaAdminService(SlaPolicyRepository slaPolicyRepository,
                           BusinessCalendarRepository businessCalendarRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.businessCalendarRepository = businessCalendarRepository;
    }

    @Transactional(readOnly = true)
    public List<SlaPolicy> listPolicies(UUID orgId) {
        return slaPolicyRepository.findByOrgIdAndAppliesTo(orgId, SlaPolicy.AppliesTo.INCIDENT);
    }

    @Transactional
    public SlaPolicy createPolicy(UUID orgId, UUID userId, SlaPolicyRequest request) {
        SlaPolicy policy = new SlaPolicy();
        policy.setOrgId(orgId);
        policy.setName(request.name());
        policy.setAppliesTo(request.appliesTo());
        policy.setPriorityFilter(request.priorityFilter());
        policy.setResponseTargetMinutes(request.responseTargetMinutes());
        policy.setResolutionTargetMinutes(request.resolutionTargetMinutes());
        if (request.businessHoursCalendarId() != null) {
            BusinessCalendar calendar = businessCalendarRepository.findByIdAndOrgId(request.businessHoursCalendarId(), orgId)
                    .orElseThrow(() -> new NotFoundException("Business calendar not found"));
            policy.setBusinessHoursCalendar(calendar);
        }
        policy.setCreatedBy(userId);
        policy.setUpdatedBy(userId);
        return slaPolicyRepository.save(policy);
    }

    @Transactional
    public SlaPolicy updatePolicy(UUID orgId, UUID policyId, UUID userId, SlaPolicyRequest request) {
        SlaPolicy policy = slaPolicyRepository.findByIdAndOrgId(policyId, orgId)
                .orElseThrow(() -> new NotFoundException("SLA policy not found"));
        policy.setName(request.name());
        policy.setAppliesTo(request.appliesTo());
        policy.setPriorityFilter(request.priorityFilter());
        policy.setResponseTargetMinutes(request.responseTargetMinutes());
        policy.setResolutionTargetMinutes(request.resolutionTargetMinutes());
        if (request.businessHoursCalendarId() != null) {
            BusinessCalendar calendar = businessCalendarRepository.findByIdAndOrgId(request.businessHoursCalendarId(), orgId)
                    .orElseThrow(() -> new NotFoundException("Business calendar not found"));
            policy.setBusinessHoursCalendar(calendar);
        } else {
            policy.setBusinessHoursCalendar(null);
        }
        policy.setUpdatedBy(userId);
        return slaPolicyRepository.save(policy);
    }

    @Transactional
    public void deletePolicy(UUID orgId, UUID policyId) {
        SlaPolicy policy = slaPolicyRepository.findByIdAndOrgId(policyId, orgId)
                .orElseThrow(() -> new NotFoundException("SLA policy not found"));
        policy.setDeletedAt(java.time.OffsetDateTime.now());
        slaPolicyRepository.save(policy);
    }

    @Transactional(readOnly = true)
    public List<BusinessCalendar> listCalendars(UUID orgId) {
        return businessCalendarRepository.findByOrgId(orgId);
    }

    @Transactional
    public BusinessCalendar createCalendar(UUID orgId, UUID userId, BusinessCalendarRequest request) {
        BusinessCalendar calendar = new BusinessCalendar();
        calendar.setOrgId(orgId);
        calendar.setName(request.name());
        calendar.setTimezone(request.timezone());
        calendar.setWorkingHours(request.workingHours());
        calendar.setHolidays(request.holidays());
        calendar.setCreatedBy(userId);
        calendar.setUpdatedBy(userId);
        return businessCalendarRepository.save(calendar);
    }

    @Transactional
    public BusinessCalendar updateCalendar(UUID orgId, UUID calendarId, UUID userId, BusinessCalendarRequest request) {
        BusinessCalendar calendar = businessCalendarRepository.findByIdAndOrgId(calendarId, orgId)
                .orElseThrow(() -> new NotFoundException("Business calendar not found"));
        calendar.setName(request.name());
        calendar.setTimezone(request.timezone());
        calendar.setWorkingHours(request.workingHours());
        calendar.setHolidays(request.holidays());
        calendar.setUpdatedBy(userId);
        return businessCalendarRepository.save(calendar);
    }

    @Transactional
    public void deleteCalendar(UUID orgId, UUID calendarId) {
        BusinessCalendar calendar = businessCalendarRepository.findByIdAndOrgId(calendarId, orgId)
                .orElseThrow(() -> new NotFoundException("Business calendar not found"));
        calendar.setDeletedAt(java.time.OffsetDateTime.now());
        businessCalendarRepository.save(calendar);
    }
}
