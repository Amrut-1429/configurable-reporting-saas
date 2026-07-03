package com.reporting.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "report_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "group_by_field")
    private String groupByField;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> metrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> columns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pivot_rows", columnDefinition = "jsonb")
    private List<String> pivotRows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pivot_columns", columnDefinition = "jsonb")
    private List<String> pivotColumns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pivot_values", columnDefinition = "jsonb")
    private List<java.util.Map<String, Object>> pivotValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "formulas", columnDefinition = "jsonb")
    private List<java.util.Map<String, Object>> formulas;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_formulas", columnDefinition = "jsonb")
    private List<java.util.Map<String, Object>> columnFormulas;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pivot_filters", columnDefinition = "jsonb")
    private List<java.util.Map<String, Object>> pivotFilters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pivot_relationships", columnDefinition = "jsonb")
    private List<java.util.Map<String, Object>> pivotRelationships;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditional_formatting", columnDefinition = "jsonb")
    private List<java.util.Map<String, Object>> conditionalFormatting;

    @Column(name = "workspace")
    @Builder.Default
    private String workspace = "LATEST";
}
