package com.reporting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "uploaded_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "file_hash")
    private String fileHash;

    private Long size;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "report_source_id")
    @com.fasterxml.jackson.annotation.JsonBackReference(value = "source-files")
    private ReportSource reportSource;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "normalization_status")
    private String normalizationStatus = "PENDING"; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(name = "total_rows")
    private Integer totalRows = 0;

    @Column(name = "processed_rows")
    private Integer processedRows = 0;

    @Column(name = "failed_rows")
    private Integer failedRows = 0;

    @Column(name = "workspace")
    @Builder.Default
    private String workspace = "Default Workspace";

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
