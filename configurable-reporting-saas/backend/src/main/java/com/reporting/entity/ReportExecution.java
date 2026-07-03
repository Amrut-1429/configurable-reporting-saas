package com.reporting.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "report_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private GeneratedReport report;

    private String format; // EXCEL, CSV, PDF

    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;

    private String status; // GENERATING, COMPLETED, FAILED

    private Long rowCount;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    private String filePath; // Path where the generated file is saved locally

    @PrePersist
    public void prePersist() {
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = "GENERATING";
        }
    }
}
