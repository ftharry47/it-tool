package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.ServiceRequest;

import java.util.Map;
import java.util.Set;

public final class ServiceRequestStatusMachine {

    private static final Map<ServiceRequest.Status, Set<ServiceRequest.Status>> COMMON = Map.ofEntries(
            Map.entry(ServiceRequest.Status.SUBMITTED, Set.of(ServiceRequest.Status.PENDING_APPROVAL, ServiceRequest.Status.CANCELLED)),
            Map.entry(ServiceRequest.Status.PENDING_APPROVAL, Set.of(ServiceRequest.Status.APPROVED, ServiceRequest.Status.REJECTED, ServiceRequest.Status.CANCELLED)),
            Map.entry(ServiceRequest.Status.APPROVED, Set.of(ServiceRequest.Status.IN_FULFILLMENT, ServiceRequest.Status.CANCELLED)),
            Map.entry(ServiceRequest.Status.IN_FULFILLMENT, Set.of(ServiceRequest.Status.FULFILLED, ServiceRequest.Status.CANCELLED)),
            Map.entry(ServiceRequest.Status.REJECTED, Set.of(ServiceRequest.Status.CANCELLED)),
            Map.entry(ServiceRequest.Status.FULFILLED, Set.of()),
            Map.entry(ServiceRequest.Status.CANCELLED, Set.of())
    );

    private ServiceRequestStatusMachine() {
    }

    public static void validate(ServiceRequest request, boolean allTasksCompleted, ServiceRequest.Status to) {
        if (request.getStatus() == to) {
            return;
        }

        CatalogItem item = request.getCatalogItem();
        boolean approvalRequired = item != null && item.isApprovalRequired();

        if (request.getStatus() == ServiceRequest.Status.SUBMITTED && to == ServiceRequest.Status.IN_FULFILLMENT && !approvalRequired) {
            // non-approval-required items skip approval
        } else if (!COMMON.getOrDefault(request.getStatus(), Set.of()).contains(to)) {
            throw new IllegalStateException("Illegal service request status transition: " + request.getStatus() + " -> " + to);
        }

        if (to == ServiceRequest.Status.FULFILLED && !allTasksCompleted) {
            throw new IllegalStateException("Cannot mark FULFILLED until all fulfillment tasks are completed");
        }
    }
}
