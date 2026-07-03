package com.reporting.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reporting.dto.ReportRequestDTO;
import com.reporting.entity.GeneratedReport;
import com.reporting.entity.ReportExecution;
import com.reporting.entity.User;
import com.reporting.repository.GeneratedReportRepository;
import com.reporting.repository.ReportExecutionRepository;
import com.reporting.repository.UserRepository;
import com.reporting.repository.ColumnMappingRepository;
import com.reporting.service.ExportService;
import com.reporting.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ReportController {

    private final ReportService reportService;
    private final ExportService exportService;
    private final GeneratedReportRepository generatedReportRepository;
    private final ReportExecutionRepository reportExecutionRepository;
    private final UserRepository userRepository;
    private final ColumnMappingRepository columnMappingRepository;
    private final ObjectMapper objectMapper;
    private final com.reporting.repository.ReportTemplateRepository reportTemplateRepository;

    public ReportController(ReportService reportService, ExportService exportService,
                            GeneratedReportRepository generatedReportRepository,
                            ReportExecutionRepository reportExecutionRepository,
                            UserRepository userRepository,
                            ColumnMappingRepository columnMappingRepository,
                            ObjectMapper objectMapper,
                            com.reporting.repository.ReportTemplateRepository reportTemplateRepository) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.generatedReportRepository = generatedReportRepository;
        this.reportExecutionRepository = reportExecutionRepository;
        this.userRepository = userRepository;
        this.columnMappingRepository = columnMappingRepository;
        this.objectMapper = objectMapper;
        this.reportTemplateRepository = reportTemplateRepository;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateReport(@RequestBody ReportRequestDTO request) {
        try {
            return ResponseEntity.ok(reportService.generateReport(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error generating report: " + e.getMessage());
        }
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveReport(@RequestBody SaveReportRequestDTO request, Authentication auth) {
        try {
            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();

            com.reporting.entity.ReportTemplate template = new com.reporting.entity.ReportTemplate();
            template.setName("Dynamic-" + java.util.UUID.randomUUID().toString());
            template.setReportType("AD_HOC");
            
            if (request.getTemplate() != null) {
                template.setGroupByField(request.getTemplate().getGroupByField());
                template.setMetrics(request.getTemplate().getMetrics());
                template.setColumns(request.getTemplate().getColumns());
                template.setPivotRows(request.getTemplate().getPivotRows());
                template.setPivotColumns(request.getTemplate().getPivotColumns());
                template.setPivotValues(request.getTemplate().getPivotValues());
                template.setFormulas(request.getTemplate().getFormulas());
                template.setPivotFilters(request.getTemplate().getPivotFilters());
                template.setPivotRelationships(request.getTemplate().getPivotRelationships());
                template.setConditionalFormatting(request.getTemplate().getConditionalFormatting());
            } else {
                template.setGroupByField(request.getGroupBy());
                template.setMetrics(request.getMetrics());
            }
            template = reportTemplateRepository.save(template);

            GeneratedReport report = new GeneratedReport();
            report.setName(request.getName());
            report.setCreatedBy(user);
            report.setTemplate(template);
            report.setFile(request.getFile());
            report.setSnapshotMode(request.getSnapshotMode());
            report.setFilters(request.getFilters());
            
            generatedReportRepository.save(report);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error saving report: " + e.getMessage());
        }
    }

    @GetMapping("/saved")
    public ResponseEntity<?> getSavedReports(Authentication auth) {
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(generatedReportRepository.findByCreatedByIdOrderByCreatedAtDesc(user.getId()));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<?> previewReport(@PathVariable Long id) {
        try {
            GeneratedReport report = generatedReportRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Report not found"));
            ReportRequestDTO request = new ReportRequestDTO();
            if (report.getTemplate() != null) {
                request.setTemplateId(report.getTemplate().getId());
            }
            // Auto refresh check: only lock to historical file if snapshotMode is TRUE
            if (report.getSnapshotMode() != null && report.getSnapshotMode()) {
                if (report.getFile() != null) {
                    request.setUploadedFileId(report.getFile().getId());
                }
            }
            if (report.getFilters() != null && !report.getFilters().isEmpty()) {
                request.setFilters(objectMapper.readValue(report.getFilters(), Map.class));
            }
            return ResponseEntity.ok(reportService.generateReport(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error previewing report: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateReport(@PathVariable Long id, @RequestBody SaveReportRequestDTO request) {
        try {
            GeneratedReport report = generatedReportRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Report not found"));
            
            report.setName(request.getName());
            report.setSnapshotMode(request.getSnapshotMode());
            report.setFilters(request.getFilters());
            
            if (report.getTemplate() != null && request.getTemplate() != null) {
                com.reporting.entity.ReportTemplate template = report.getTemplate();
                template.setGroupByField(request.getTemplate().getGroupByField());
                template.setMetrics(request.getTemplate().getMetrics());
                template.setColumns(request.getTemplate().getColumns());
                template.setPivotRows(request.getTemplate().getPivotRows());
                template.setPivotColumns(request.getTemplate().getPivotColumns());
                template.setPivotValues(request.getTemplate().getPivotValues());
                template.setFormulas(request.getTemplate().getFormulas());
                template.setPivotFilters(request.getTemplate().getPivotFilters());
                template.setPivotRelationships(request.getTemplate().getPivotRelationships());
                template.setConditionalFormatting(request.getTemplate().getConditionalFormatting());
                reportTemplateRepository.save(template);
            }
            
            generatedReportRepository.save(report);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating report: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<?> duplicateReport(@PathVariable Long id) {
        try {
            GeneratedReport original = generatedReportRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Report not found"));

            com.reporting.entity.ReportTemplate origTemplate = original.getTemplate();
            com.reporting.entity.ReportTemplate newTemplate = new com.reporting.entity.ReportTemplate();
            newTemplate.setName("Dynamic-" + java.util.UUID.randomUUID().toString());
            newTemplate.setReportType("AD_HOC");
            if (origTemplate != null) {
                newTemplate.setGroupByField(origTemplate.getGroupByField());
                newTemplate.setMetrics(origTemplate.getMetrics());
                newTemplate.setColumns(origTemplate.getColumns());
                newTemplate.setPivotRows(origTemplate.getPivotRows());
                newTemplate.setPivotColumns(origTemplate.getPivotColumns());
                newTemplate.setPivotValues(origTemplate.getPivotValues());
                newTemplate.setFormulas(origTemplate.getFormulas());
                newTemplate.setPivotFilters(origTemplate.getPivotFilters());
                newTemplate.setPivotRelationships(origTemplate.getPivotRelationships());
                newTemplate.setConditionalFormatting(origTemplate.getConditionalFormatting());
                newTemplate = reportTemplateRepository.save(newTemplate);
            }

            GeneratedReport copy = new GeneratedReport();
            copy.setName(original.getName() + " (Copy)");
            copy.setCreatedBy(original.getCreatedBy());
            copy.setTemplate(newTemplate);
            copy.setFile(original.getFile());
            copy.setSnapshotMode(original.getSnapshotMode());
            copy.setFilters(original.getFilters());

            generatedReportRepository.save(copy);
            return ResponseEntity.ok(copy);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error duplicating report: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReport(@PathVariable Long id) {
        try {
            GeneratedReport report = generatedReportRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Report not found"));
            generatedReportRepository.delete(report);
            if (report.getTemplate() != null) {
                reportTemplateRepository.delete(report.getTemplate());
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting report: " + e.getMessage());
        }
    }

    @GetMapping("/files/{fileId}/sheets-columns")
    public ResponseEntity<?> getFileSheetsAndColumns(@PathVariable Long fileId) {
        try {
            List<com.reporting.entity.ColumnMapping> mappings = columnMappingRepository.findByFileId(fileId);
            Map<String, List<String>> sheetsMap = new LinkedHashMap<>();
            for (com.reporting.entity.ColumnMapping m : mappings) {
                String sheet = m.getSheetName() != null ? m.getSheetName() : "Sheet1";
                sheetsMap.computeIfAbsent(sheet, k -> new ArrayList<>()).add(m.getExcelColumn());
            }
            return ResponseEntity.ok(sheetsMap);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error getting sheets and columns: " + e.getMessage());
        }
    }

    @GetMapping("/workspace/sheets-columns")
    public ResponseEntity<?> getWorkspaceSheetsAndColumns(@RequestParam(value = "workspace", required = false) String workspace) {
        try {
            List<com.reporting.entity.UploadedFile> latestFiles = reportService.getLatestCompletedFilesForWorkspace(workspace);
            Map<String, List<String>> workspaceMap = new LinkedHashMap<>();

            for (com.reporting.entity.UploadedFile file : latestFiles) {
                String logicalName;
                if (file.getReportSource() != null) {
                    logicalName = file.getReportSource().getInternalKey();
                } else {
                    logicalName = reportService.getBaseName(file.getFileName());
                }

                List<com.reporting.entity.ColumnMapping> mappings = columnMappingRepository.findByFileId(file.getId());
                List<String> cols = workspaceMap.computeIfAbsent(logicalName, k -> new ArrayList<>());

                for (com.reporting.entity.ColumnMapping m : mappings) {
                    if (!cols.contains(m.getExcelColumn())) {
                        cols.add(m.getExcelColumn());
                    }
                }
            }
            return ResponseEntity.ok(workspaceMap);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error getting workspace sheets and columns: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<?> exportReportById(@PathVariable Long id, @RequestParam String format) {
        try {
            GeneratedReport report = generatedReportRepository.findById(id).orElseThrow(() -> new RuntimeException("Report not found"));
            
            ReportExecution execution = new ReportExecution();
            execution.setReport(report);
            execution.setFormat(format.toUpperCase());
            execution.setStatus("GENERATING");
            execution.setStartedAt(java.time.LocalDateTime.now());
            reportExecutionRepository.save(execution);
            
            ReportRequestDTO request = new ReportRequestDTO();
            if (report.getTemplate() != null) {
                request.setTemplateId(report.getTemplate().getId());
            }
            // Auto refresh check: only lock to historical file if snapshotMode is TRUE
            if (report.getSnapshotMode() != null && report.getSnapshotMode()) {
                if (report.getFile() != null) {
                    request.setUploadedFileId(report.getFile().getId());
                }
            }
            if (report.getFilters() != null && !report.getFilters().isEmpty()) {
                request.setFilters(objectMapper.readValue(report.getFilters(), Map.class));
            }

            byte[] data;
            String contentType;
            String ext;

            if ("csv".equalsIgnoreCase(format)) {
                data = exportService.exportToCsv(request);
                contentType = "text/csv";
                ext = ".csv";
            } else if ("excel".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
                data = exportService.exportToExcel(request);
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                ext = ".xlsx";
            } else if ("pdf".equalsIgnoreCase(format)) {
                data = exportService.exportToPdf(request);
                contentType = "application/pdf";
                ext = ".pdf";
            } else {
                execution.setStatus("FAILED");
                execution.setErrorMessage("Unsupported format");
                execution.setCompletedAt(java.time.LocalDateTime.now());
                reportExecutionRepository.save(execution);
                return ResponseEntity.badRequest().body("Unsupported format");
            }
            
            execution.setStatus("COMPLETED");
            execution.setRowCount((long) data.length); // storing bytes as row count for simplicity
            execution.setCompletedAt(java.time.LocalDateTime.now());
            reportExecutionRepository.save(execution);

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + report.getName().replace(" ", "_") + ext)
                    .header("Content-Type", contentType)
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error exporting report: " + e.getMessage());
        }
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportAdhocReport(@RequestBody ReportRequestDTO request, @RequestParam String format) {
        try {
            byte[] data;
            String contentType;
            String ext;

            if ("csv".equalsIgnoreCase(format)) {
                data = exportService.exportToCsv(request);
                contentType = "text/csv";
                ext = ".csv";
            } else if ("excel".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
                data = exportService.exportToExcel(request);
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                ext = ".xlsx";
            } else if ("pdf".equalsIgnoreCase(format)) {
                data = exportService.exportToPdf(request);
                contentType = "application/pdf";
                ext = ".pdf";
            } else {
                return ResponseEntity.badRequest().body("Unsupported format");
            }

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=report" + ext)
                    .header("Content-Type", contentType)
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error exporting report: " + e.getMessage());
        }
    }

    @PostMapping("/relationships/preview")
    public ResponseEntity<?> previewRelationship(@RequestBody Map<String, Object> rel) {
        try {
            Map<String, Object> preview = reportService.previewRelationship(rel);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error previewing relationship: " + e.getMessage());
        }
    }
}
