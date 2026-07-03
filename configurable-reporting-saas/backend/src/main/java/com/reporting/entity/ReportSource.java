package com.reporting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "report_sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "internal_key", nullable = false, unique = true)
    private String internalKey;

    @Column(name = "table_number", nullable = false)
    private Integer tableNumber;

    @OneToMany(mappedBy = "reportSource", fetch = FetchType.EAGER)
    @com.fasterxml.jackson.annotation.JsonManagedReference(value = "source-files")
    private List<UploadedFile> files;
}
