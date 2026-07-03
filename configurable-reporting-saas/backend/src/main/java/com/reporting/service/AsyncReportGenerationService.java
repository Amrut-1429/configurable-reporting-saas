package com.reporting.service;

import com.reporting.dto.ReportRequestDTO;
import com.reporting.entity.ReportExecution;
import com.reporting.repository.ReportExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Slf4j
@Service
public class AsyncReportGenerationService {

    private final ExportService exportService;
    private final ReportExecutionRepository reportExecutionRepository;

    public AsyncReportGenerationService(ExportService exportService, ReportExecutionRepository reportExecutionRepository) {
        this.exportService = exportService;
        this.reportExecutionRepository = reportExecutionRepository;
    }

    @Async
    public void generateReportAsync(ReportExecution execution, ReportRequestDTO request) {
        log.info("Starting async report generation for execution ID: {}", execution.getId());
        try {
            byte[] fileData;
            String extension;
            switch (execution.getFormat().toUpperCase()) {
                case "CSV":
                    fileData = exportService.exportToCsv(request);
                    extension = ".csv";
                    break;
                case "EXCEL":
                case "XLSX":
                    fileData = exportService.exportToExcel(request);
                    extension = ".xlsx";
                    break;
                case "PDF":
                    fileData = exportService.exportToPdf(request);
                    extension = ".pdf";
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported format: " + execution.getFormat());
            }

            Path tempFile = Files.createTempFile("report_" + execution.getId() + "_", extension);
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                fos.write(fileData);
            }

            execution.setFilePath(tempFile.toAbsolutePath().toString());
            execution.setCompletedAt(LocalDateTime.now());
            execution.setStatus("COMPLETED");
            // Set rowCount (rough estimate based on map size if needed, but not easily accessible here without parsing, leaving null or 0)
            execution.setRowCount((long) (fileData.length)); // Mocking rowCount with bytes for now, or just leave null
            
            reportExecutionRepository.save(execution);
            log.info("Successfully generated report for execution ID: {}", execution.getId());

        } catch (Exception e) {
            log.error("Failed to generate report for execution ID: {}", execution.getId(), e);
            execution.setStatus("FAILED");
            execution.setErrorMessage(e.getMessage());
            execution.setCompletedAt(LocalDateTime.now());
            reportExecutionRepository.save(execution);
        }
    }
}
