package com.reporting.repository;

import com.reporting.entity.ReportExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportExecutionRepository extends JpaRepository<ReportExecution, Long> {
    List<ReportExecution> findByReportIdOrderByStartedAtDesc(Long reportId);
}
