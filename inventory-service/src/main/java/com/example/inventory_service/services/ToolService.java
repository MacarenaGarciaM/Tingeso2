package com.example.inventory_service.services;

import com.example.inventory_service.entities.ToolEntity;
import com.example.inventory_service.repositories.ToolRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

@Service
public class ToolService {

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private RestTemplate restTemplate;

    // Service name registered in Eureka
    private static final String KARDEX_SERVICE_URL = "http://kardex-service/kardex/movements";

    private static final List<String> validState =
            Arrays.asList("Disponible", "Prestada", "En reparación", "Dada de baja");

    // Create Tool
    public ToolEntity saveTool(ToolEntity tool, String rutUser) {

        if (tool.getName() == null || tool.getName().isBlank())
            throw new IllegalArgumentException("Tool name is required");

        if (tool.getCategory() == null || tool.getCategory().isBlank())
            throw new IllegalArgumentException("Tool category is required");

        if (tool.getRepositionValue() <= 0)
            throw new IllegalArgumentException("Reposition value must be > 0");

        if (tool.getAmount() <= 0)
            throw new IllegalArgumentException("Amount must be > 0");

        if (!validState.contains(tool.getInitialState()))
            throw new IllegalArgumentException("Invalid initial state");

        List<ToolEntity> existing = toolRepository
                .findByNameAndCategoryAndInitialState(
                        tool.getName(), tool.getCategory(), tool.getInitialState()
                );

        ToolEntity savedTool;

        if (!existing.isEmpty()) {
            ToolEntity bucket = existing.get(0);
            bucket.setAmount(bucket.getAmount() + tool.getAmount());
            bucket.setRepositionValue(tool.getRepositionValue());
            bucket.setAvailable("Disponible".equalsIgnoreCase(bucket.getInitialState()));
            savedTool = toolRepository.save(bucket);
        } else {
            ToolEntity newTool = new ToolEntity(
                    null,
                    tool.getName(),
                    tool.getCategory(),
                    tool.getInitialState(),
                    tool.getRepositionValue(),
                    "Disponible".equalsIgnoreCase(tool.getInitialState()),
                    tool.getAmount()
            );
            savedTool = toolRepository.save(newTool);
        }

        // Register kardex via HTTP (snapshot)
        registerKardexMovement(
                savedTool.getId(),
                savedTool.getName(),
                savedTool.getCategory(),
                rutUser,
                "Ingreso",
                tool.getAmount()
        );

        return savedTool;
    }

    // Update tool
    public ToolEntity updateTool(Long id, String newState, Integer newAmount,
                                 Integer newRepositionValue, String rutUser) {

        ToolEntity tool = toolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found"));

        // ==========================
        // 1) MOVE STATE (bucket logic)
        // ==========================
        if (newState != null) {

            if (!validState.contains(newState))
                throw new IllegalArgumentException("Invalid state");

            if (tool.getAmount() <= 0)
                throw new IllegalArgumentException("No stock available");

            // Reduce origin bucket by 1
            tool.setAmount(tool.getAmount() - 1);
            toolRepository.save(tool);

            Optional<ToolEntity> targetOpt =
                    toolRepository.findFirstByNameAndCategoryAndInitialState(
                            tool.getName(), tool.getCategory(), newState
                    );

            ToolEntity target = targetOpt.orElseGet(() ->
                    new ToolEntity(
                            null,
                            tool.getName(),
                            tool.getCategory(),
                            newState,
                            tool.getRepositionValue(),
                            "Disponible".equalsIgnoreCase(newState),
                            0
                    )
            );

            target.setAmount(target.getAmount() + 1);
            ToolEntity savedTarget = toolRepository.save(target);

            // Kardex movement for the DESTINATION bucket (snapshot = name/category)
            registerKardexMovement(
                    savedTarget.getId(),
                    savedTarget.getName(),
                    savedTarget.getCategory(),
                    rutUser,
                    "Cambio de estado: " + newState,
                    savedTarget.getAmount()
            );

            return savedTarget;
        }

        // ==========================
        // 2) EDIT ATTRIBUTES (same row)
        // ==========================
        boolean changed = false;

        if (newAmount != null) {
            if (newAmount < 0) throw new IllegalArgumentException("Amount cannot be negative");
            tool.setAmount(newAmount);
            changed = true;
        }

        if (newRepositionValue != null) {
            if (newRepositionValue < 0) throw new IllegalArgumentException("Reposition value cannot be negative");
            tool.setRepositionValue(newRepositionValue);
            changed = true;
        }

        ToolEntity saved = toolRepository.save(tool);

        // ✅ Opcional (recomendado): registrar cambios de edición en Kardex también
        // Si NO quieres kardex cuando solo se edita amount/repositionValue, comenta este bloque.
        if (changed) {
            String type = "Actualización herramienta";
            registerKardexMovement(
                    saved.getId(),
                    saved.getName(),
                    saved.getCategory(),
                    rutUser,
                    type,
                    saved.getAmount()
            );
        }

        return saved;
    }

    public ToolEntity getToolByName(String name) {
        return toolRepository.findByName(name)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tool not found"));
    }

    public List<ToolEntity> listAvailable() {
        return toolRepository
                .findAllByInitialStateIgnoreCaseAndAmountGreaterThan("Disponible", 0);
    }

    public List<ToolEntity> listByState(String state) {
        if ("Disponible".equalsIgnoreCase(state)) {
            return toolRepository
                    .findAllByInitialStateIgnoreCaseAndAmountGreaterThan(state, 0);
        }
        return toolRepository.findAllByInitialStateIgnoreCase(state);
    }

    public List<NameCategory> getAllNamesWithCategory() {
        List<ToolEntity> tools = toolRepository.findAll();

        Map<String, NameCategory> unique = new LinkedHashMap<>();
        for (ToolEntity t : tools) {
            if (t.getName() == null || t.getCategory() == null) continue;
            String key = t.getName() + "||" + t.getCategory();
            unique.putIfAbsent(key, new NameCategory(t.getName(), t.getCategory()));
        }
        return new ArrayList<>(unique.values());
    }

    public List<Long> getIdsByNameCategoryAndState(String name, String category, String state) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category is required");
        if (state == null || state.isBlank()) throw new IllegalArgumentException("state is required");
        return toolRepository.findIdsByNameCategoryAndState(name.trim(), category.trim(), state.trim());
    }

    private void registerKardexMovement(
            Long toolId,
            String toolNameSnapshot,
            String toolCategorySnapshot,
            String rutUser,
            String type,
            int stock
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("toolId", toolId);
        body.put("toolNameSnapshot", toolNameSnapshot);
        body.put("toolCategorySnapshot", toolCategorySnapshot);
        body.put("rutUser", rutUser);
        body.put("type", type);
        body.put("movementDate", LocalDate.now());
        body.put("stock", stock);

        restTemplate.postForEntity(
                KARDEX_SERVICE_URL,
                body,
                Void.class
        );
    }

    public ToolEntity getById(Long id) {
        return toolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + id));
    }

    // Aux class
    @Data
    @AllArgsConstructor
    public static class NameCategory {
        private String name;
        private String category;
    }
}
