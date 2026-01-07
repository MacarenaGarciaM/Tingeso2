package com.example.loanservice.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

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



    @ManyToOne
    @JoinColumn(name = "loan_id")
    @JsonBackReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoanEntity loan;

    @Column(name = "tool_id", nullable = false)
    private Long toolId;

    private String toolNameSnapshot;





}
