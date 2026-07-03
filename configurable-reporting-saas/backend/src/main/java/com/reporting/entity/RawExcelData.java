package com.reporting.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "raw_excel_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawExcelData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "raw_data_seq")
    @SequenceGenerator(name = "raw_data_seq", sequenceName = "raw_data_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private UploadedFile file;

    @Column(name = "sheet_name")
    private String sheetName;

    @Column(name = "row_number")
    private Integer rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data;
}
