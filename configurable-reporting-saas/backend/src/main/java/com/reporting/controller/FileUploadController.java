package com.reporting.controller;

import com.reporting.dto.ColumnDto;
import com.reporting.dto.ColumnMappingResponse;
import com.reporting.entity.ColumnMapping;
import com.reporting.entity.UploadedFile;
import com.reporting.entity.User;
import com.reporting.repository.ColumnMappingRepository;
import com.reporting.repository.UserRepository;
import com.reporting.service.FileUploadService;
import com.reporting.service.NormalizationService;
import com.reporting.entity.ReportSource;
import com.reporting.repository.ReportSourceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final NormalizationService normalizationService;
    private final ColumnMappingRepository columnMappingRepository;
    private final UserRepository userRepository;
    private final com.reporting.repository.UploadedFileRepository uploadedFileRepository;
    private final com.reporting.repository.TransactionRepository transactionRepository;
    private final ReportSourceRepository reportSourceRepository;

    public FileUploadController(FileUploadService fileUploadService,
                                NormalizationService normalizationService,
                                ColumnMappingRepository columnMappingRepository,
                                UserRepository userRepository,
                                com.reporting.repository.UploadedFileRepository uploadedFileRepository,
                                com.reporting.repository.TransactionRepository transactionRepository,
                                ReportSourceRepository reportSourceRepository) {
        this.fileUploadService = fileUploadService;
        this.normalizationService = normalizationService;
        this.columnMappingRepository = columnMappingRepository;
        this.userRepository = userRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.transactionRepository = transactionRepository;
        this.reportSourceRepository = reportSourceRepository;
    }

    @GetMapping
    public ResponseEntity<List<UploadedFile>> getFiles(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(uploadedFileRepository.findByUploadedByIdOrderByCreatedAtDesc(user.getId()));
    }

    @GetMapping("/workspaces")
    public ResponseEntity<List<String>> getWorkspaces() {
        try {
            List<UploadedFile> files = uploadedFileRepository.findAll();
            List<String> workspaces = files.stream()
                    .map(UploadedFile::getWorkspace)
                    .filter(w -> w != null && !w.trim().isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
            if (!workspaces.contains("Default Workspace")) {
                workspaces.add("Default Workspace");
            }
            return ResponseEntity.ok(workspaces);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "workspace", required = false) String workspace,
                                        Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
            
            UploadedFile uploadedFile = fileUploadService.processFile(file, user, workspace);
            return ResponseEntity.ok(uploadedFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error uploading file: " + e.getMessage());
        }
    }

    @GetMapping("/{fileId}/status")
    public ResponseEntity<?> getFileStatus(@PathVariable Long fileId) {
        return uploadedFileRepository.findById(fileId)
                .map(file -> ResponseEntity.ok(java.util.Map.of(
                        "status", file.getNormalizationStatus(),
                        "totalRows", file.getTotalRows(),
                        "processedRows", file.getProcessedRows() != null ? file.getProcessedRows() : 0,
                        "failedRows", file.getFailedRows() != null ? file.getFailedRows() : 0
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{fileId}/mapping")
    public ResponseEntity<ColumnMappingResponse> getMapping(@PathVariable Long fileId) {
        List<ColumnMapping> mappings = columnMappingRepository.findByFileId(fileId);
        List<ColumnDto> dtos = mappings.stream()
                .map(m -> ColumnDto.builder()
                        .excelColumn(m.getExcelColumn())
                        .mappedField(m.getMappedField())
                        .dataType(m.getDataType())
                        .build())
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(new ColumnMappingResponse(dtos));
    }

    @PostMapping("/{fileId}/mapping/confirm")
    public ResponseEntity<?> confirmMapping(@PathVariable Long fileId, @RequestBody List<ColumnDto> mappings) {
        try {
            List<ColumnMapping> existingMappings = columnMappingRepository.findByFileId(fileId);
            columnMappingRepository.deleteAll(existingMappings);
            
            UploadedFile file = uploadedFileRepository.findById(fileId).orElseThrow();
            
            List<ColumnMapping> newMappings = mappings.stream().map(dto -> ColumnMapping.builder()
                    .file(file)
                    .excelColumn(dto.getExcelColumn())
                    .mappedField(dto.getMappedField())
                    .dataType(dto.getDataType() != null ? dto.getDataType() : "STRING")
                    .build()).collect(Collectors.toList());
                    
            columnMappingRepository.saveAll(newMappings);
            
            normalizationService.normalizeData(fileId);
            
            return ResponseEntity.ok("Mappings confirmed");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error confirming mapping: " + e.getMessage());
        }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteFile(@PathVariable Long fileId, Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
            fileUploadService.deleteFile(fileId, user.getId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting file: " + e.getMessage());
        }
    }

    @PostMapping("/{fileId}/renormalize")
    public ResponseEntity<?> renormalizeFile(@PathVariable Long fileId) {
        normalizationService.normalizeData(fileId);
        return ResponseEntity.ok(Map.of("message", "Normalization re-triggered"));
    }

    @GetMapping("/{fileId}/data")
    public ResponseEntity<?> getFileData(@PathVariable Long fileId, 
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "100") int size) {
        org.springframework.data.domain.Page<com.reporting.entity.Transaction> transactions = 
            transactionRepository.findByFileId(fileId, org.springframework.data.domain.PageRequest.of(page, size));
            
        org.springframework.data.domain.Page<com.reporting.dto.TransactionDTO> dtoPage = transactions.map(t -> 
            com.reporting.dto.TransactionDTO.builder()
                .id(t.getId())
                .transactionDate(t.getTransactionDate())
                .invoiceNumber(t.getInvoiceNumber())
                .locationName(t.getLocation() != null ? t.getLocation().getName() : null)
                .productNumber(t.getProduct() != null ? t.getProduct().getPartNumber() : null)
                .productName(t.getProduct() != null ? t.getProduct().getPartName() : null)
                .saleAmount(t.getSaleAmount())
                .purchaseAmount(t.getPurchaseAmount())
                .expenseAmount(t.getExpenseAmount())
                .quantity(t.getQuantity())
                .taxAmount(t.getTaxAmount())
                .build()
        );
        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping("/sources")
    public ResponseEntity<List<ReportSource>> getReportSources() {
        List<ReportSource> sources = reportSourceRepository.findAll();
        // Sort by tableNumber ascending
        sources.sort(java.util.Comparator.comparing(ReportSource::getTableNumber));
        return ResponseEntity.ok(sources);
    }

    @PostMapping("/sources")
    public ResponseEntity<?> createReportSource(@RequestBody ReportSource source) {
        try {
            if (source.getName() == null || source.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Name is required");
            }
            if (source.getInternalKey() == null || source.getInternalKey().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Internal Key is required");
            }
            // Sanitize internal key to uppercase alphanumeric/underscore
            source.setInternalKey(source.getInternalKey().toUpperCase().replaceAll("[^A-Z0-9_]", "_"));
            
            if (source.getTableNumber() == null) {
                long count = reportSourceRepository.count();
                source.setTableNumber((int) count + 1);
            }
            return ResponseEntity.ok(reportSourceRepository.save(source));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating report source: " + e.getMessage());
        }
    }

    @DeleteMapping("/sources/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteReportSource(@PathVariable Long id) {
        try {
            ReportSource source = reportSourceRepository.findById(id).orElseThrow();
            List<UploadedFile> files = uploadedFileRepository.findByReportSourceId(id);
            for (UploadedFile f : files) {
                fileUploadService.deleteFile(f.getId(), null);
            }
            reportSourceRepository.delete(source);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting report source: " + e.getMessage());
        }
    }

    @PostMapping("/sources/{sourceId}/upload")
    public ResponseEntity<?> uploadFileForSource(@PathVariable Long sourceId,
                                                 @RequestParam("file") MultipartFile file,
                                                 Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
            
            ReportSource source = reportSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Report Source not found"));
            
            UploadedFile uploadedFile = fileUploadService.processFileForSource(file, user, source);
            return ResponseEntity.ok(uploadedFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error uploading file: " + e.getMessage());
        }
    }

    @PostMapping("/{fileId}/replace")
    public ResponseEntity<?> replaceFile(@PathVariable Long fileId,
                                         @RequestParam("file") MultipartFile file,
                                         Authentication authentication) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
            
            UploadedFile oldFile = uploadedFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
            ReportSource source = oldFile.getReportSource();
            if (source == null) {
                return ResponseEntity.badRequest().body("This file is not associated with a Report Source");
            }
            
            fileUploadService.deleteFile(fileId, user.getId());
            UploadedFile newFile = fileUploadService.processFileForSource(file, user, source);
            return ResponseEntity.ok(newFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error replacing file: " + e.getMessage());
        }
    }
}
