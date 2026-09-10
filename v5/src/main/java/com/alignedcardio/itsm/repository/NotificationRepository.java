package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Notification> findByEmailStatusAndChannelIn(
            Notification.DeliveryStatus emailStatus,
            List<Notification.Channel> channels);
}
