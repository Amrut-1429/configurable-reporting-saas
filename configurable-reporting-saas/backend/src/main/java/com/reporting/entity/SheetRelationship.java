package com.reporting.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sheet_relationships")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SheetRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "source_sheet", nullable = false)
    private String sourceSheet;

    @Column(name = "source_field", nullable = false)
    private String sourceField;

    @Column(name = "target_sheet", nullable = false)
    private String targetSheet;

    @Column(name = "target_field", nullable = false)
    private String targetField;

    @Column(name = "join_type", nullable = false)
    private String joinType; // INNER or LEFT
}
