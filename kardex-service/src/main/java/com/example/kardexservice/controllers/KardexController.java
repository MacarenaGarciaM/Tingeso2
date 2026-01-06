package com.example.kardexservice.controllers;

import com.example.kardexservice.entities.KardexEntity;
import com.example.kardexservice.repositories.KardexRepository;
import com.example.kardexservice.services.KardexService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/kardex")
@CrossOrigin("*")
@RequiredArgsConstructor
public class KardexController {

    private final KardexService kardexService;
    private final KardexRepository kardexRepository;

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Page<KardexEntity>> list(
            @RequestParam(required = false) Long toolId,
            @RequestParam(required = false) String rutUser,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "movementDate,desc") String sort
    ) {
        rutUser  = (rutUser != null && rutUser.isBlank()) ? null : rutUser;
        type     = (type != null && type.isBlank()) ? null : type;
        name     = (name != null && name.isBlank()) ? null : name;
        category = (category != null && category.isBlank()) ? null : category;

        String[] s = sort.split(",", 2);
        Sort.Direction dir = (s.length > 1 && "asc".equalsIgnoreCase(s[1]))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Sort sortObj = Sort.by(dir, s[0]);
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.max(size, 1), sortObj);

        Page<KardexEntity> out = kardexService.search(toolId, rutUser, type, start, end, name, category, pr);
        return ResponseEntity.ok(out);
    }


    @PostMapping("/movements")
    public ResponseEntity<KardexEntity> create(@RequestBody KardexEntity body) {

        // validaciones mínimas
        if (body.getToolId() == null) throw new IllegalArgumentException("toolId is required");
        if (body.getToolNameSnapshot() == null || body.getToolNameSnapshot().isBlank())
            throw new IllegalArgumentException("toolNameSnapshot is required");
        if (body.getToolCategorySnapshot() == null || body.getToolCategorySnapshot().isBlank())
            throw new IllegalArgumentException("toolCategorySnapshot is required");
        if (body.getRutUser() == null || body.getRutUser().isBlank())
            throw new IllegalArgumentException("rutUser is required");
        if (body.getType() == null || body.getType().isBlank())
            throw new IllegalArgumentException("type is required");
        if (body.getMovementDate() == null) body.setMovementDate(LocalDate.now());
        if (body.getStock() == null) body.setStock(0);

        // asegurar que se cree como nuevo
        body.setId(null);

        return ResponseEntity.ok(kardexRepository.save(body));
    }
}
