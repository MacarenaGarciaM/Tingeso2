package com.example.loanservice.controllers;

import com.example.loanservice.repositories.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/loan/exists")
public class LoanChecksController {

    private final LoanRepository loanRepository;

    @GetMapping("/overdue")
    public boolean existsOverdue(@RequestParam("rut") String rut) {
        LocalDate today = LocalDate.now();
        return loanRepository.existsByRutUserAndReturnDateBeforeAndLateReturnDateIsNull(rut, today);
    }

    @GetMapping("/unpaid-latefine")
    public boolean existsUnpaidLateFine(@RequestParam("rut") String rut) {
        return loanRepository.existsByRutUserAndLateFineGreaterThanAndLateFinePaidIsFalse(rut, 0);
    }

    @GetMapping("/unpaid-damage")
    public boolean existsUnpaidDamage(@RequestParam("rut") String rut) {
        return loanRepository.existsByRutUserAndDamagePenaltyGreaterThanAndDamagePenaltyPaidIsFalse(rut, 0);
    }
}
