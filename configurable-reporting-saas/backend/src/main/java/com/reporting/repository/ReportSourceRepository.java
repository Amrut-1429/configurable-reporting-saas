package com.reporting.repository;

import com.reporting.entity.ReportSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportSourceRepository extends JpaRepository<ReportSource, Long> {
    Optional<ReportSource> findByInternalKey(String internalKey);
}
