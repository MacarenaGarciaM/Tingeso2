package com.example.loanservice.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "loan_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"loan_id","tool_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    @JsonBackReference
    private LoanEntity loan;

    @Column(name = "tool_id", nullable = false)
    private Long toolId;

    private String toolNameSnapshot;
}
