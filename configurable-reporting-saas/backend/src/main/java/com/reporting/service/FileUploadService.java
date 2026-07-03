package com.reporting.service;

import com.opencsv.CSVReader;
import com.reporting.entity.ColumnMapping;
import com.reporting.entity.RawExcelData;
import com.reporting.entity.UploadedFile;
import com.reporting.entity.User;
import com.reporting.entity.ReportSource;
import com.reporting.repository.ColumnMappingRepository;
import com.reporting.repository.RawExcelDataRepository;
import com.reporting.repository.UploadedFileRepository;
import com.reporting.repository.TransactionRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class FileUploadService {

    private final UploadedFileRepository uploadedFileRepository;
    private final RawExcelDataRepository rawExcelDataRepository;
    private final ColumnMappingRepository columnMappingRepository;
    private final TransactionRepository transactionRepository;
    private final NormalizationService normalizationService;
    private final org.springframework.context.ApplicationContext applicationContext;
    private final jakarta.persistence.EntityManager entityManager;

    public FileUploadService(UploadedFileRepository uploadedFileRepository,
                             RawExcelDataRepository rawExcelDataRepository,
                             ColumnMappingRepository columnMappingRepository,
                             TransactionRepository transactionRepository,
                             NormalizationService normalizationService,
                             org.springframework.context.ApplicationContext applicationContext,
                             jakarta.persistence.EntityManager entityManager) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.rawExcelDataRepository = rawExcelDataRepository;
        this.columnMappingRepository = columnMappingRepository;
        this.transactionRepository = transactionRepository;
        this.normalizationService = normalizationService;
        this.applicationContext = applicationContext;
        this.entityManager = entityManager;
    }

    @Transactional
    public void deleteFile(Long fileId, Long userId) {
        UploadedFile file = uploadedFileRepository.findById(fileId)
            .orElseThrow(() -> new IllegalArgumentException("File not found"));
            
        if (userId != null && !file.getUploadedBy().getId().equals(userId)) {
            throw new IllegalArgumentException("You do not have permission to delete this file");
        }
        
        entityManager.createNativeQuery("UPDATE generated_reports SET file_id = NULL WHERE file_id = :fileId")
            .setParameter("fileId", fileId)
            .executeUpdate();
            
        transactionRepository.deleteByFileId(fileId);
        rawExcelDataRepository.deleteByFileId(fileId);
        columnMappingRepository.deleteByFileId(fileId);
        uploadedFileRepository.deleteById(fileId);
    }

    @Transactional
    public UploadedFile processFile(MultipartFile file, User user) throws Exception {
        return processFile(file, user, "Default Workspace");
    }

    @Transactional
    public UploadedFile processFile(MultipartFile file, User user, String workspace) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".csv") && !fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            throw new IllegalArgumentException("Invalid file type");
        }

        String targetWorkspace = workspace != null ? workspace : "Default Workspace";
        String fileHash = org.springframework.util.DigestUtils.md5DigestAsHex(file.getInputStream());
        java.util.Optional<UploadedFile> existingFile = uploadedFileRepository.findByFileHashAndWorkspace(fileHash, targetWorkspace);
        if (!existingFile.isPresent()) {
            existingFile = uploadedFileRepository.findByFileHash(fileHash);
            if (existingFile.isPresent() && !targetWorkspace.equals(existingFile.get().getWorkspace())) {
                existingFile = java.util.Optional.empty();
            }
        }
        if (existingFile.isPresent()) {
            return existingFile.get();
        }

        UploadedFile uploadedFile = UploadedFile.builder()
                .fileName(fileName)
                .fileType(fileName.substring(fileName.lastIndexOf('.') + 1))
                .size(file.getSize())
                .fileHash(fileHash)
                .uploadedBy(user)
                .normalizationStatus("PROCESSING")
                .workspace(targetWorkspace)
                .build();
        uploadedFile = uploadedFileRepository.save(uploadedFile);

        java.io.File tempFile = java.io.File.createTempFile("upload-", fileName);
        file.transferTo(tempFile);

        FileUploadService proxy = applicationContext.getBean(FileUploadService.class);
        proxy.parseFileAsync(tempFile, uploadedFile, fileName);

        return uploadedFile;
    }

    @Transactional
    public UploadedFile processFileForSource(MultipartFile file, User user, ReportSource source) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".csv") && !fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            throw new IllegalArgumentException("Invalid file type");
        }

        String fileHash = org.springframework.util.DigestUtils.md5DigestAsHex(file.getInputStream());
        java.util.Optional<UploadedFile> existingFile = uploadedFileRepository.findByFileHashAndReportSourceId(fileHash, source.getId());
        if (existingFile.isPresent()) {
            return existingFile.get();
        }

        UploadedFile uploadedFile = UploadedFile.builder()
                .fileName(fileName)
                .fileType(fileName.substring(fileName.lastIndexOf('.') + 1))
                .size(file.getSize())
                .fileHash(fileHash)
                .uploadedBy(user)
                .normalizationStatus("PROCESSING")
                .workspace("Default Workspace")
                .reportSource(source)
                .build();
        uploadedFile = uploadedFileRepository.save(uploadedFile);

        java.io.File tempFile = java.io.File.createTempFile("upload-", fileName);
        file.transferTo(tempFile);

        FileUploadService proxy = applicationContext.getBean(FileUploadService.class);
        proxy.parseFileAsync(tempFile, uploadedFile, fileName);

        return uploadedFile;
    }

    @org.springframework.scheduling.annotation.Async
    public void parseFileAsync(java.io.File file, UploadedFile uploadedFile, String fileName) {
        try {
            if (fileName.endsWith(".csv")) {
                processCsv(file, uploadedFile);
            } else {
                processExcel(file, uploadedFile);
            }
            
            // Retrieve the fresh state from database to avoid race conditions
            UploadedFile freshFile = uploadedFileRepository.findById(uploadedFile.getId()).orElse(uploadedFile);
            if (!"COMPLETED".equalsIgnoreCase(freshFile.getNormalizationStatus())) {
                freshFile.setNormalizationStatus("PENDING");
                uploadedFileRepository.save(freshFile);
            }

            // Automatically trigger normalization since all raw rows are parsed and saved!
            normalizationService.normalizeData(uploadedFile.getId());
        } catch (Exception e) {
            UploadedFile freshFile = uploadedFileRepository.findById(uploadedFile.getId()).orElse(uploadedFile);
            freshFile.setNormalizationStatus("FAILED");
            uploadedFileRepository.save(freshFile);
        } finally {
            file.delete();
        }
    }

    private void processCsv(java.io.File file, UploadedFile uploadedFile) throws Exception {
        try (CSVReader reader = new CSVReader(new java.io.FileReader(file))) {
            String[] rawHeaders = reader.readNext();
            if (rawHeaders == null) return;

            boolean isTsv = rawHeaders.length == 1 && rawHeaders[0] != null && rawHeaders[0].contains("\t");
            if (isTsv) {
                rawHeaders = rawHeaders[0].split("\t");
            }

            String[] headers = new String[rawHeaders.length];
            for (int i = 0; i < rawHeaders.length; i++) {
                headers[i] = rawHeaders[i] != null ? rawHeaders[i].replace("\0", "").trim() : "";
            }

            detectAndSaveMappings(headers, uploadedFile, "Sheet1");

            String[] line;
            List<RawExcelData> rawDataList = new ArrayList<>();
            int rowNum = 1;
            while ((line = reader.readNext()) != null) {
                if (isTsv && line.length == 1 && line[0] != null) {
                    line = line[0].split("\t");
                }
                
                Map<String, Object> data = new HashMap<>();
                for (int i = 0; i < headers.length && i < line.length; i++) {
                    String val = line[i] != null ? line[i].replace("\0", "").trim() : "";
                    data.put(headers[i], val);
                }
                rawDataList.add(RawExcelData.builder()
                        .file(uploadedFile)
                        .sheetName("Sheet1")
                        .rowNumber(rowNum++)
                        .data(data)
                        .build());
            }
            rawExcelDataRepository.saveAll(rawDataList);
        }
    }

    private void processExcel(java.io.File file, UploadedFile uploadedFile) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new java.io.FileInputStream(file))) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                Iterator<Row> rowIterator = sheet.iterator();
                
                if (!rowIterator.hasNext()) continue;
                
                Row headerRow = rowIterator.next();
                List<String> headers = new ArrayList<>();
                for (Cell cell : headerRow) {
                    String h = getCellValueAsString(cell);
                    headers.add(h != null ? h.trim() : "");
                }
                
                detectAndSaveMappings(headers.toArray(new String[0]), uploadedFile, sheetName);

                List<RawExcelData> rawDataList = new ArrayList<>();
                int rowNum = 1;
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    Map<String, Object> data = new HashMap<>();
                    boolean hasData = false;
                    for (int i = 0; i < headers.size(); i++) {
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        String val = getCellValueAsString(cell);
                        data.put(headers.get(i), val != null ? val.trim() : "");
                        if (val != null && !val.trim().isEmpty()) {
                            hasData = true;
                        }
                    }
                    if (hasData) {
                        rawDataList.add(RawExcelData.builder()
                                .file(uploadedFile)
                                .sheetName(sheetName)
                                .rowNumber(rowNum++)
                                .data(data)
                                .build());
                    }
                }
                if (!rawDataList.isEmpty()) {
                    rawExcelDataRepository.saveAll(rawDataList);
                }
            }
        }
    }

    private void detectAndSaveMappings(String[] headers, UploadedFile file, String sheetName) {
        List<ColumnMapping> existing = columnMappingRepository.findByFileId(file.getId());
        java.util.Set<String> existingCols = new java.util.HashSet<>();
        for (ColumnMapping m : existing) {
            existingCols.add(m.getExcelColumn().trim().toLowerCase());
        }

        List<ColumnMapping> newMappings = new ArrayList<>();
        for (String header : headers) {
            String sanitizedHeader = header != null ? header.replace("\0", "") : "";
            if (sanitizedHeader.trim().isEmpty()) continue;
            if (sanitizedHeader.length() > 255) {
                sanitizedHeader = sanitizedHeader.substring(0, 255);
            }
            if (existingCols.contains(sanitizedHeader.trim().toLowerCase())) {
                continue;
            }
            String mappedField = inferMappedField(sanitizedHeader);
            if ("IGNORE".equals(mappedField)) {
                mappedField = "UNKNOWN"; // Keep all fields so they can be mapped and selected in pivot reports
            }
            
            String dataType = inferDataType(mappedField);
            
            newMappings.add(ColumnMapping.builder()
                    .file(file)
                    .sheetName(sheetName)
                    .excelColumn(sanitizedHeader)
                    .mappedField(mappedField)
                    .dataType(dataType)
                    .build());
        }
        if (!newMappings.isEmpty()) {
            columnMappingRepository.saveAll(newMappings);
        }
    }

    private String inferMappedField(String header) {
        if (header == null) return "IGNORE";
        String clean = header.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        
        if (clean.contains("location") || clean.contains("station") || clean.contains("city") || clean.contains("branch") || clean.contains("warehouse")) {
            return "LOCATION";
        }
        if (clean.equals("amount") || clean.equals("netamount") || clean.contains("saleamount") || clean.contains("invoiceamount") || clean.contains("lineiteminvoicetotal") || clean.contains("totalinvoiceamount")) {
            return "SALE_AMOUNT";
        }
        if (clean.contains("purchaseamount") || clean.contains("poamount") || clean.contains("purchasevalue")) {
            return "PURCHASE_AMOUNT";
        }
        if (clean.contains("tax") || clean.equals("cgst") || clean.equals("sgst") || clean.equals("igst") || clean.equals("utgst") || clean.equals("vat")) {
            return "TAX";
        }
        if (clean.contains("date") && !clean.contains("up") && !clean.contains("irn")) {
            return "DATE";
        }
        if (clean.contains("qty") || clean.contains("quantity")) {
            return "QUANTITY";
        }
        if (clean.contains("part") || clean.contains("product")) {
            if (clean.contains("name") || clean.contains("description")) return "PRODUCT_NAME";
            return "PRODUCT";
        }
        if (clean.contains("invoice") && (clean.contains("no") || clean.contains("num"))) {
            return "INVOICE_NUMBER";
        }
        return "UNKNOWN";
    }
    
    private String inferDataType(String mappedField) {
        switch (mappedField) {
            case "SALE_AMOUNT":
            case "PURCHASE_AMOUNT":
            case "TAX":
            case "QUANTITY":
                return "NUMBER";
            case "DATE":
                return "DATE";
            default:
                return "STRING";
        }
    }

    private String getCellValueAsString(Cell cell) {
        String value = "";
        switch (cell.getCellType()) {
            case STRING: 
                value = cell.getStringCellValue();
                break;
            case NUMERIC: 
                if (DateUtil.isCellDateFormatted(cell)) {
                    value = cell.getLocalDateTimeCellValue().toString();
                } else {
                    value = String.valueOf(cell.getNumericCellValue());
                }
                break;
            case BOOLEAN: 
                value = String.valueOf(cell.getBooleanCellValue());
                break;
            default: 
                value = "";
                break;
        }
        return value != null ? value.replace("\0", "") : "";
    }
}
