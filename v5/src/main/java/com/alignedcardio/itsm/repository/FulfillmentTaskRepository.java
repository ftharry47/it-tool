package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.FulfillmentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FulfillmentTaskRepository extends JpaRepository<FulfillmentTask, UUID> {

    List<FulfillmentTask> findByServiceRequestIdOrderBySequenceOrderAsc(UUID serviceRequestId);

    List<FulfillmentTask> findByServiceRequest_IdInAndAssigneeIsNotNull(java.util.List<UUID> serviceRequestIds);

    boolean existsByServiceRequestIdAndStatus(UUID serviceRequestId, FulfillmentTask.Status status);

    List<FulfillmentTask> findByExpectedDeliveryDateIsNotNullAndDeliveredAtIsNull();

    List<FulfillmentTask> findByAssignee_IdAndStatusInOrderByServiceRequest_CreatedAtDesc(
            UUID assigneeId, java.util.Collection<FulfillmentTask.Status> statuses);

    List<FulfillmentTask> findByAssignee_IdAndAssignedAtBetween(
            UUID assigneeId, java.time.OffsetDateTime from, java.time.OffsetDateTime to);

    List<FulfillmentTask> findByAssignee_IdAndDeletedAtIsNull(UUID assigneeId);
}
