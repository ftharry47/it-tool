package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.automation.AutomationRuleCreateRequest;
import com.alignedcardio.itsm.api.automation.AutomationRuleResponse;
import com.alignedcardio.itsm.api.automation.AutomationRuleUpdateRequest;
import com.alignedcardio.itsm.entity.AutomationRule;
import com.alignedcardio.itsm.repository.AutomationRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AutomationRuleService {

    private final AutomationRuleRepository ruleRepository;

    public AutomationRuleService(AutomationRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Transactional
    public AutomationRuleResponse create(UUID orgId, UUID createdBy, AutomationRuleCreateRequest request) {
        AutomationRule rule = new AutomationRule();
        rule.setOrgId(orgId);
        rule.setName(request.name());
        rule.setDescription(request.description());
        rule.setTriggerType(request.triggerType());
        rule.setTriggerEntity(request.triggerEntity());
        rule.setTriggerConfig(request.triggerConfig());
        rule.setConditions(request.conditions());
        rule.setActions(request.actions());
        rule.setActive(request.active());
        rule.setCreatedBy(createdBy);
        rule.setUpdatedBy(createdBy);
        rule.setCreatedAt(OffsetDateTime.now());
        rule.setUpdatedAt(OffsetDateTime.now());
        return toResponse(ruleRepository.save(rule));
    }

    @Transactional(readOnly = true)
    public List<AutomationRuleResponse> list(UUID orgId) {
        return ruleRepository.findByOrgIdAndDeletedAtIsNullOrderByUpdatedAtDesc(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AutomationRuleResponse get(UUID orgId, UUID ruleId) {
        AutomationRule rule = ruleRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, ruleId)
                .orElseThrow(() -> new NotFoundException("Automation rule not found"));
        return toResponse(rule);
    }

    @Transactional
    public AutomationRuleResponse update(UUID orgId, UUID updatedBy, UUID ruleId, AutomationRuleUpdateRequest request) {
        AutomationRule rule = ruleRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, ruleId)
                .orElseThrow(() -> new NotFoundException("Automation rule not found"));

        if (request.name() != null) rule.setName(request.name());
        if (request.description() != null) rule.setDescription(request.description());
        if (request.triggerType() != null) rule.setTriggerType(request.triggerType());
        if (request.triggerEntity() != null) rule.setTriggerEntity(request.triggerEntity());
        if (request.triggerConfig() != null) rule.setTriggerConfig(request.triggerConfig());
        if (request.conditions() != null) rule.setConditions(request.conditions());
        if (request.actions() != null) rule.setActions(request.actions());
        if (request.active() != null) rule.setActive(request.active());
        rule.setUpdatedBy(updatedBy);
        rule.setUpdatedAt(OffsetDateTime.now());
        return toResponse(ruleRepository.save(rule));
    }

    @Transactional
    public void delete(UUID orgId, UUID deletedBy, UUID ruleId) {
        AutomationRule rule = ruleRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, ruleId)
                .orElseThrow(() -> new NotFoundException("Automation rule not found"));
        rule.setDeletedAt(OffsetDateTime.now());
        rule.setUpdatedBy(deletedBy);
        rule.setUpdatedAt(OffsetDateTime.now());
        ruleRepository.save(rule);
    }

    private AutomationRuleResponse toResponse(AutomationRule rule) {
        return new AutomationRuleResponse(
                rule.getId(),
                rule.getOrgId(),
                rule.getName(),
                rule.getDescription(),
                rule.getTriggerType(),
                rule.getTriggerEntity(),
                rule.getTriggerConfig(),
                rule.getConditions(),
                rule.getActions(),
                rule.isActive(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
