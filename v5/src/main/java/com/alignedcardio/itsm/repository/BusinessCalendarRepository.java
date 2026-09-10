package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.BusinessCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessCalendarRepository extends JpaRepository<BusinessCalendar, UUID> {

    List<BusinessCalendar> findByOrgId(UUID orgId);

    Optional<BusinessCalendar> findByIdAndOrgId(UUID id, UUID orgId);
}
