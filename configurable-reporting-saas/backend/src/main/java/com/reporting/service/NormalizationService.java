package com.reporting.service;

import com.reporting.entity.*;
import com.reporting.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class NormalizationService {

    private final RawExcelDataRepository rawExcelDataRepository;
    private final ColumnMappingRepository columnMappingRepository;
    private final TransactionRepository transactionRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;

    public NormalizationService(RawExcelDataRepository rawExcelDataRepository,
                                ColumnMappingRepository columnMappingRepository,
                                TransactionRepository transactionRepository,
                                UploadedFileRepository uploadedFileRepository,
                                LocationRepository locationRepository,
                                ProductRepository productRepository) {
        this.rawExcelDataRepository = rawExcelDataRepository;
        this.columnMappingRepository = columnMappingRepository;
        this.transactionRepository = transactionRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    @org.springframework.scheduling.annotation.Async
    public void normalizeData(Long fileId) {
        UploadedFile file = uploadedFileRepository.findById(fileId).orElseThrow();
        if ("PROCESSING".equalsIgnoreCase(file.getNormalizationStatus())) {
            log.info("File {} is still parsing. Normalization will be triggered automatically upon parsing completion.", fileId);
            return;
        }
        file.setNormalizationStatus("NORMALIZING");
        uploadedFileRepository.save(file);

        // Remove previous rows for this file if we are re-running normalization
        transactionRepository.deleteByFileId(fileId);

        List<ColumnMapping> mappings = columnMappingRepository.findByFileId(fileId);
        List<RawExcelData> rawDataList = rawExcelDataRepository.findByFileId(fileId);
        
        int totalRows = rawDataList.size();
        int processedRows = 0;
        int failedRows = 0;

        for (RawExcelData rawData : rawDataList) {
            Map<String, Object> data = rawData.getData();
            Transaction transaction = new Transaction();
            transaction.setFile(file);

            boolean rowHasError = false;
            String errorReason = "";
            
            String locationName = null;
            String partNumber = null;
            String partName = null;
            String partCategory = null;

            for (ColumnMapping mapping : mappings) {
                String excelCol = mapping.getExcelColumn();
                String mappedField = mapping.getMappedField();
                Object rawValueObj = data.get(excelCol);
                
                if (rawValueObj == null) continue;
                String rawValue = rawValueObj.toString().trim();
                if (rawValue.isEmpty()) continue;

                try {
                    switch (mappedField) {
                        case "LOCATION":
                            locationName = rawValue;
                            break;
                        case "PRODUCT":
                        case "PRODUCT_NUMBER":
                            partNumber = rawValue;
                            break;
                        case "PRODUCT_NAME":
                            partName = rawValue;
                            break;
                        case "PRODUCT_CATEGORY":
                            partCategory = rawValue;
                            break;
                        case "SALE_AMOUNT":
                        case "AMOUNT":
                            transaction.setSaleAmount(parseBigDecimal(rawValue));
                            break;
                        case "PURCHASE_AMOUNT":
                            transaction.setPurchaseAmount(parseBigDecimal(rawValue));
                            break;
                        case "QUANTITY":
                            transaction.setQuantity(parseBigDecimal(rawValue));
                            break;
                        case "TAX":
                            transaction.setTaxAmount(parseBigDecimal(rawValue));
                            break;
                        case "DATE":
                            transaction.setTransactionDate(parseDate(rawValue));
                            break;
                        case "INVOICE":
                        case "INVOICE_NUMBER":
                            transaction.setInvoiceNumber(rawValue);
                            break;
                    }
                } catch (Exception e) {
                    rowHasError = true;
                    errorReason = "Error mapping field '" + mappedField + "' with value '" + rawValue + "': " + e.getMessage();
                    break;
                }
            }

            if (rowHasError) {
                log.error("Row {} failed normalization: {}", rawData.getRowNumber(), errorReason);
                failedRows++;
            } else {
                try {
                    // Resolve Location
                    Location location = null;
                    if (locationName != null) {
                        Optional<Location> locOpt = locationRepository.findByNameIgnoreCase(locationName);
                        if (locOpt.isPresent()) {
                            location = locOpt.get();
                        } else {
                            location = locationRepository.save(Location.builder().name(locationName).build());
                        }
                    }
                    transaction.setLocation(location);
                    
                    // Resolve Product
                    Product product = null;
                    if (partNumber != null) {
                        Optional<Product> prodOpt = productRepository.findByPartNumberIgnoreCase(partNumber);
                        if (prodOpt.isPresent()) {
                            product = prodOpt.get();
                            boolean needsUpdate = false;
                            // Update part name if provided but missing
                            if (partName != null && product.getPartName() == null) {
                                product.setPartName(partName);
                                needsUpdate = true;
                            }
                            if (partCategory != null && product.getCategory() == null) {
                                product.setCategory(partCategory);
                                needsUpdate = true;
                            }
                            if (needsUpdate) {
                                productRepository.save(product);
                            }
                        } else {
                            product = productRepository.save(Product.builder().partNumber(partNumber).partName(partName).category(partCategory).build());
                        }
                    }
                    transaction.setProduct(product);

                    // Deduplication logic: check for existing matching Location/Product/Date
                    if (transaction.getLocation() != null && transaction.getProduct() != null && transaction.getTransactionDate() != null &&
                        transactionRepository.existsByLocationAndProductAndTransactionDate(
                            transaction.getLocation(),
                            transaction.getProduct(),
                            transaction.getTransactionDate())) {
                        log.warn("Row {} ignored (Duplicate Location/Product/Date)", rawData.getRowNumber());
                    } else {
                        transactionRepository.save(transaction);
                        processedRows++;
                    }
                } catch (Exception e) {
                    log.error("Row {} failed to save: {}", rawData.getRowNumber(), e.getMessage());
                    failedRows++;
                }
            }
        }

        file.setTotalRows(totalRows);
        file.setProcessedRows(processedRows);
        file.setFailedRows(failedRows);
        file.setNormalizationStatus("COMPLETED");
        uploadedFileRepository.save(file);
        
        log.info("Normalization for File {} completed. Total: {}, Processed: {}, Failed/Ignored: {}", fileId, totalRows, processedRows, totalRows - processedRows);
    }
    
    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.trim().isEmpty()) return BigDecimal.ZERO;
        String clean = val.replaceAll("[^0-9.\\-]", "");
        if (clean.isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(clean);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr.contains("T")) {
            return LocalDate.parse(dateStr.substring(0, 10)); // simple truncation for ISO dates
        }
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yy"),
                DateTimeFormatter.ofPattern("M/d/yy")
        };
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("Invalid date format: " + dateStr);
    }
}
