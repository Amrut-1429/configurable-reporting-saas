package com.reporting.controller;

import com.reporting.service.NormalizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/normalization")
@CrossOrigin(origins = "*", maxAge = 3600)
public class NormalizationController {

    private final NormalizationService normalizationService;

    public NormalizationController(NormalizationService normalizationService) {
        this.normalizationService = normalizationService;
    }

    @PostMapping("/process/{fileId}")
    public ResponseEntity<?> processFile(@PathVariable Long fileId) {
        try {
            normalizationService.normalizeData(fileId);
            return ResponseEntity.ok("Data normalized successfully for file: " + fileId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error normalizing data: " + e.getMessage());
        }
    }
}
