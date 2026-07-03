package com.reporting.repository;

import com.reporting.entity.RawExcelData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RawExcelDataRepository extends JpaRepository<RawExcelData, Long> {
    List<RawExcelData> findByFileId(Long fileId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM RawExcelData r WHERE r.file.id = :fileId")
    void deleteByFileId(@org.springframework.data.repository.query.Param("fileId") Long fileId);
}
