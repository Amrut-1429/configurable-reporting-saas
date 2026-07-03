package com.reporting.repository;

import com.reporting.entity.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
    java.util.List<UploadedFile> findByUploadedByIdOrderByCreatedAtDesc(Long userId);
    
    java.util.Optional<UploadedFile> findByFileHash(String fileHash);
    
    java.util.Optional<UploadedFile> findByFileHashAndWorkspace(String fileHash, String workspace);

    java.util.List<UploadedFile> findByReportSourceId(Long reportSourceId);

    java.util.Optional<UploadedFile> findByFileHashAndReportSourceId(String fileHash, Long reportSourceId);
}
