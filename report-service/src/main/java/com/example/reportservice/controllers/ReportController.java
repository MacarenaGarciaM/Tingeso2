package com.example.reportservice.controllers;

import com.example.reportservice.services.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reports")
@CrossOrigin("*")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/active-loans")
    public ResponseEntity<List<Map<String, Object>>> activeLoans(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return ResponseEntity.ok(reportService.activeLoans(start, end));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/overdue-clients")
    public ResponseEntity<List<Map<String, Object>>> overdueClients(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return ResponseEntity.ok(reportService.overdueClients(start, end));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/top-tools")
    public ResponseEntity<List<Map<String, Object>>> topTools(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(reportService.topTools(start, end, limit));
    }
}
