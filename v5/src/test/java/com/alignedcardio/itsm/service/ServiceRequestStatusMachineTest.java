package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.ServiceRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceRequestStatusMachineTest {

    private ServiceRequest createRequest(boolean approvalRequired) {
        ServiceRequest request = new ServiceRequest();
        request.setApprovalRequired(approvalRequired);
        return request;
    }

    @Test
    void approvalRequiredGoesToPendingApproval() {
        ServiceRequest request = createRequest(true);
        request.setStatus(ServiceRequest.Status.SUBMITTED);
        assertDoesNotThrow(
                () -> ServiceRequestStatusMachine.validate(request, false, ServiceRequest.Status.PENDING_APPROVAL));
    }

    @Test
    void noApprovalGoesStraightToInFulfillment() {
        ServiceRequest request = createRequest(false);
        request.setStatus(ServiceRequest.Status.SUBMITTED);
        assertDoesNotThrow(
                () -> ServiceRequestStatusMachine.validate(request, false, ServiceRequest.Status.IN_FULFILLMENT));
    }

    @Test
    void approvalRequiredCannotSkipApproval() {
        ServiceRequest request = createRequest(true);
        request.setStatus(ServiceRequest.Status.SUBMITTED);
        assertThrows(IllegalStateException.class,
                () -> ServiceRequestStatusMachine.validate(request, false, ServiceRequest.Status.IN_FULFILLMENT));
    }

    @Test
    void approvedGoesToInFulfillment() {
        ServiceRequest request = createRequest(true);
        request.setStatus(ServiceRequest.Status.APPROVED);
        assertDoesNotThrow(
                () -> ServiceRequestStatusMachine.validate(request, false, ServiceRequest.Status.IN_FULFILLMENT));
    }

    @Test
    void inFulfillmentGoesToFulfilledWhenAllTasksComplete() {
        ServiceRequest request = createRequest(false);
        request.setStatus(ServiceRequest.Status.IN_FULFILLMENT);
        assertDoesNotThrow(
                () -> ServiceRequestStatusMachine.validate(request, true, ServiceRequest.Status.FULFILLED));
    }

    @Test
    void cannotMarkFulfilledWithIncompleteTasks() {
        ServiceRequest request = createRequest(false);
        request.setStatus(ServiceRequest.Status.IN_FULFILLMENT);
        assertThrows(IllegalStateException.class,
                () -> ServiceRequestStatusMachine.validate(request, false, ServiceRequest.Status.FULFILLED));
    }

    @Test
    void rejectedCanBeCancelled() {
        ServiceRequest request = createRequest(true);
        request.setStatus(ServiceRequest.Status.REJECTED);
        assertDoesNotThrow(
                () -> ServiceRequestStatusMachine.validate(request, false, ServiceRequest.Status.CANCELLED));
    }
}
