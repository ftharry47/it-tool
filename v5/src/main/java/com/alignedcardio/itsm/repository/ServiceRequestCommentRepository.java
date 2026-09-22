package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.ServiceRequestComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRequestCommentRepository extends JpaRepository<ServiceRequestComment, UUID> {

    List<ServiceRequestComment> findByServiceRequestIdOrderByCreatedAtAsc(UUID serviceRequestId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT MAX(c.createdAt) FROM ServiceRequestComment c WHERE c.serviceRequest.id = :serviceRequestId")
    java.time.OffsetDateTime findMaxCreatedAtByServiceRequestId(
            @org.springframework.data.repository.query.Param("serviceRequestId") UUID serviceRequestId);
}
