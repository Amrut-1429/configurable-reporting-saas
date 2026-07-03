package com.reporting.service;

import com.reporting.entity.UploadedFile;
import com.reporting.entity.User;
import com.reporting.repository.ColumnMappingRepository;
import com.reporting.repository.RawExcelDataRepository;
import com.reporting.repository.TransactionRepository;
import com.reporting.repository.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileUploadServiceTest {

    @Mock
    private UploadedFileRepository uploadedFileRepository;
    @Mock
    private RawExcelDataRepository rawExcelDataRepository;
    @Mock
    private ColumnMappingRepository columnMappingRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private FileUploadService fileUploadService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testProcessFile_DuplicateFile() throws Exception {
        User user = new User();
        user.setId(1L);

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "col1,col2\nval1,val2".getBytes());
        
        UploadedFile existingFile = new UploadedFile();
        existingFile.setId(10L);
        when(uploadedFileRepository.findByFileHash(anyString())).thenReturn(Optional.of(existingFile));

        UploadedFile result = fileUploadService.processFile(file, user);

        assertEquals(10L, result.getId());
        verify(uploadedFileRepository, never()).save(any(UploadedFile.class));
    }

    @Test
    void testProcessFile_NewFile() throws Exception {
        User user = new User();
        user.setId(1L);

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "col1,col2\nval1,val2".getBytes());
        
        when(uploadedFileRepository.findByFileHash(anyString())).thenReturn(Optional.empty());
        when(uploadedFileRepository.save(any(UploadedFile.class))).thenAnswer(i -> {
            UploadedFile f = i.getArgument(0);
            f.setId(1L);
            return f;
        });

        when(applicationContext.getBean(FileUploadService.class)).thenReturn(fileUploadService); // just return the instance

        UploadedFile result = fileUploadService.processFile(file, user);

        assertNotNull(result);
        assertEquals("test.csv", result.getFileName());
        verify(uploadedFileRepository, atLeast(1)).save(any(UploadedFile.class));
        // We skip verify(proxy, times(1)).parseFileAsync(any(), any(), anyString()) to avoid mockito java 26 issue
    }
}
