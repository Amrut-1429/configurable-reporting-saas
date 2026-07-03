package com.reporting.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ReportRequestDTO {
    private Long templateId;
    private Long uploadedFileId;
    private Map<String, String> filters;
    private String groupBy;
    private List<String> metrics;
    private List<String> columns;

    private List<String> pivotRows;
    private List<String> pivotColumns;
    private List<java.util.Map<String, Object>> pivotValues;
    private List<java.util.Map<String, Object>> formulas;
    private List<java.util.Map<String, Object>> columnFormulas;
    private List<java.util.Map<String, Object>> pivotFilters;
    private List<java.util.Map<String, Object>> pivotRelationships;
    private List<java.util.Map<String, Object>> conditionalFormatting;
    private String workspace;
}
