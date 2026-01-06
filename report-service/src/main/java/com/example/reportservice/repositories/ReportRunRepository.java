package com.example.reportservice.repositories;

import com.example.reportservice.entities.ReportRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRunRepository extends JpaRepository<ReportRunEntity, Long> { }
