package com.example.kardexservice.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "kardex")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KardexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(unique = true, nullable = false)
    private Long id;

    // referencia (no relación JPA)
    @Column(name = "tool_id", nullable = false)
    private Long toolId;

    // snapshot para filtrar sin depender de inventory-service
    @Column(name = "tool_name_snapshot", nullable = false, length = 120)
    private String toolNameSnapshot;

    @Column(name = "tool_category_snapshot", nullable = false, length = 120)
    private String toolCategorySnapshot;

    @Column(nullable = false, length = 20)
    private String rutUser;

    @Column(nullable = false, length = 150)
    private String type;

    @Column(nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate movementDate;

    @Column(nullable = false)
    private Integer stock;
}
