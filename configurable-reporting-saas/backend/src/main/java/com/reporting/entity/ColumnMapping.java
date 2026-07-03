package com.reporting.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "column_mappings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "column_mapping_seq")
    @SequenceGenerator(name = "column_mapping_seq", sequenceName = "column_mapping_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private UploadedFile file;

    @Column(name = "sheet_name")
    private String sheetName;

    private String excelColumn;
    private String mappedField;
    private String dataType;
}
