package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<PushSubscription> findByUserIdAndEndpointAndDeletedAtIsNull(UUID userId, String endpoint);

    Optional<PushSubscription> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
