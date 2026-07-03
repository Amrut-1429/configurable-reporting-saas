package com.reporting.repository;

import com.reporting.entity.ColumnMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ColumnMappingRepository extends JpaRepository<ColumnMapping, Long> {
    List<ColumnMapping> findByFileId(Long fileId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM ColumnMapping c WHERE c.file.id = :fileId")
    void deleteByFileId(@org.springframework.data.repository.query.Param("fileId") Long fileId);
}
