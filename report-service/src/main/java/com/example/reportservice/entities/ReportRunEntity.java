package com.example.reportservice.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "report_run")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String reportName; // "active-loans", "overdue-clients", "top-tools"

    @Column(nullable = false)
    private LocalDateTime executedAt;

    @Column(nullable = false, length = 200)
    private String requestedBy; // rut/email/subject del token

    @Column(columnDefinition = "text")
    private String paramsJson; // filtros (start/end/limit) como string
}
