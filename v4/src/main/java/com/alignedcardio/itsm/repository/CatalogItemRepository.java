package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.CatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {

    List<CatalogItem> findByOrgIdAndActiveTrueOrderByNameAsc(UUID orgId);

    Optional<CatalogItem> findByOrgIdAndId(UUID orgId, UUID id);
}
