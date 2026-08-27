package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByOrgIdAndStatusOrderByDisplayOrderAsc(UUID orgId, Category.Status status);
}
