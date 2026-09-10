package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.ServiceRequestComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRequestCommentRepository extends JpaRepository<ServiceRequestComment, UUID> {

    List<ServiceRequestComment> findByServiceRequestIdOrderByCreatedAtAsc(UUID serviceRequestId);
}
