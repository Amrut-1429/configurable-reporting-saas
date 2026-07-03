package com.reporting.integration;

import com.reporting.dto.ReportRequestDTO;
import com.reporting.entity.*;
import com.reporting.repository.*;
import com.reporting.service.FileUploadService;
import com.reporting.service.NormalizationService;
import com.reporting.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReportIntegrationTest {

    @Autowired
    private FileUploadService fileUploadService;
    @Autowired
    private NormalizationService normalizationService;
    @Autowired
    private ReportService reportService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UploadedFileRepository uploadedFileRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ColumnMappingRepository columnMappingRepository;
    @Autowired
    private RawExcelDataRepository rawExcelDataRepository;
    @Autowired
    private GeneratedReportRepository generatedReportRepository;
    @Autowired
    private ReportTemplateRepository reportTemplateRepository;
    @Autowired
    private ReportExecutionRepository reportExecutionRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        reportExecutionRepository.deleteAll();
        transactionRepository.deleteAll();
        generatedReportRepository.deleteAll();
        columnMappingRepository.deleteAll();
        rawExcelDataRepository.deleteAll();
        uploadedFileRepository.deleteAll();
        reportTemplateRepository.deleteAll();
        if (userRepository.findByEmail("test@test.com").isEmpty()) {
            testUser = User.builder().email("test@test.com").password("pwd").name("Test User").role("ROLE_USER").build();
            userRepository.save(testUser);
        } else {
            testUser = userRepository.findByEmail("test@test.com").get();
        }
    }

    @Test
    void testEndToEndFlow() throws Exception {
        // 1. Upload File
        String csvData = "Station,Date,Quantity,Amount,Tax\nBhopal,2026-06-01,10,50000,2000";
        MockMultipartFile file = new MockMultipartFile("file", "sales_test.csv", "text/csv", csvData.getBytes());

        UploadedFile uploadedFile = fileUploadService.processFile(file, testUser);
        assertNotNull(uploadedFile.getId());
        
        // Wait for async parsing to complete
        int retries = 0;
        UploadedFile checkFile = uploadedFileRepository.findById(uploadedFile.getId()).orElseThrow();
        while ("PROCESSING".equalsIgnoreCase(checkFile.getNormalizationStatus()) && retries < 100) {
            Thread.sleep(100);
            checkFile = uploadedFileRepository.findById(uploadedFile.getId()).orElseThrow();
            retries++;
        }

        // Add mappings
        columnMappingRepository.save(ColumnMapping.builder().file(uploadedFile).excelColumn("Station").mappedField("LOCATION").dataType("STRING").build());
        columnMappingRepository.save(ColumnMapping.builder().file(uploadedFile).excelColumn("Date").mappedField("DATE").dataType("DATE").build());
        columnMappingRepository.save(ColumnMapping.builder().file(uploadedFile).excelColumn("Quantity").mappedField("QUANTITY").dataType("INTEGER").build());
        columnMappingRepository.save(ColumnMapping.builder().file(uploadedFile).excelColumn("Amount").mappedField("AMOUNT").dataType("DECIMAL").build());

        // 2. Normalize Data
        normalizationService.normalizeData(uploadedFile.getId());
        
        // Wait for async normalization
        retries = 0;
        UploadedFile updatedFile = uploadedFileRepository.findById(uploadedFile.getId()).orElseThrow();
        while (("NORMALIZING".equalsIgnoreCase(updatedFile.getNormalizationStatus()) || "PENDING".equalsIgnoreCase(updatedFile.getNormalizationStatus())) && retries < 100) {
            Thread.sleep(100);
            updatedFile = uploadedFileRepository.findById(uploadedFile.getId()).orElseThrow();
            retries++;
        }
        
        assertEquals("COMPLETED", updatedFile.getNormalizationStatus());
        assertEquals(1, updatedFile.getProcessedRows());

        // 3. Generate Report
        ReportRequestDTO request = new ReportRequestDTO();
        request.setUploadedFileId(uploadedFile.getId());
        request.setGroupBy("LOCATION");
        request.setMetrics(List.of("SUM_AMOUNT"));
        
        Map<String, Object> report = reportService.generateReport(request);
        assertNotNull(report);
        assertEquals("LOCATION", report.get("groupBy"));
        
        List<Map<String, Object>> data = (List<Map<String, Object>>) report.get("data");
        assertEquals(1, data.size());
        assertEquals("Bhopal", data.get(0).get("key"));
    }
}
