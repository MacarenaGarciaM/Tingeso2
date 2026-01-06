package com.example.kardexservice.services;

import com.example.kardexservice.entities.KardexEntity;
import com.example.kardexservice.repositories.KardexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class KardexService {

    private final KardexRepository kardexRepository;

    public Page<KardexEntity> search(
            Long toolId,
            String rutUser,
            String type,
            LocalDate start,
            LocalDate end,
            String name,
            String category,
            Pageable pageable
    ) {
        String typeLower   = (type == null || type.isBlank()) ? "" : type.toLowerCase();
        String namePat     = (name == null || name.isBlank()) ? "" : "%" + name.toLowerCase() + "%";
        String categoryPat = (category == null || category.isBlank()) ? "" : "%" + category.toLowerCase() + "%";

        boolean hasFrom = (start != null);
        boolean hasTo   = (end   != null);

        return kardexRepository.search(
                toolId,
                rutUser,
                typeLower,
                hasFrom, start,
                hasTo,   end,
                namePat,
                categoryPat,
                pageable
        );
    }
}
