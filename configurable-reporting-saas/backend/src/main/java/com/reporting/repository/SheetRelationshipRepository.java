package com.reporting.repository;

import com.reporting.entity.SheetRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SheetRelationshipRepository extends JpaRepository<SheetRelationship, Long> {
}
