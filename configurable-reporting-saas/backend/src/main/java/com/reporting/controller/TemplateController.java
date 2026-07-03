package com.reporting.controller;

import com.reporting.entity.ReportTemplate;
import com.reporting.repository.ReportTemplateRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TemplateController {

    private final ReportTemplateRepository reportTemplateRepository;

    public TemplateController(ReportTemplateRepository reportTemplateRepository) {
        this.reportTemplateRepository = reportTemplateRepository;
    }

    @GetMapping
    public List<ReportTemplate> getAllTemplates() {
        return reportTemplateRepository.findAll();
    }
}
