package com.reporting.controller;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SaveReportRequestDTO {
    private String name;
    private com.reporting.entity.ReportTemplate template;
    private com.reporting.entity.UploadedFile file;
    private Boolean snapshotMode;
    private String filters;
    private String groupBy;
    private List<String> metrics;
}
