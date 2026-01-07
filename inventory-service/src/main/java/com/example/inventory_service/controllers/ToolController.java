package com.example.inventory_service.controllers;

import com.example.inventory_service.entities.ToolEntity;
import com.example.inventory_service.services.ToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tool")
@CrossOrigin("*")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    // body = ToolEntity, rutUser via query param
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping
    public ResponseEntity<?> createTool(@RequestBody ToolEntity tool,
                                        @RequestParam String rutUser) {
        try {
            return ResponseEntity.ok(toolService.saveTool(tool, rutUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // update (move state and/or edit), via query params
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTool(@PathVariable Long id,
                                        @RequestParam(required = false) String state,
                                        @RequestParam(required = false) Integer amount,
                                        @RequestParam(required = false) Integer repositionValue,
                                        @RequestParam String rutUser) {
        try {
            return ResponseEntity.ok(
                    toolService.updateTool(id, state, amount, repositionValue, rutUser)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Si implementas getAllNamesWithCategory() en ToolService
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/names-categories")
    public ResponseEntity<List<ToolService.NameCategory>> listNamesWithCategory() {
        return ResponseEntity.ok(toolService.getAllNamesWithCategory());
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/available")
    public ResponseEntity<List<ToolEntity>> listAvailable() {
        return ResponseEntity.ok(toolService.listAvailable());
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/by-state")
    public ResponseEntity<List<ToolEntity>> listByState(@RequestParam String state) {
        return ResponseEntity.ok(toolService.listByState(state));
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getToolById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(toolService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/ids")
    public ResponseEntity<?> getToolIds(
            @RequestParam String name,
            @RequestParam String category,
            @RequestParam String state
    ) {
        try {
            return ResponseEntity.ok(toolService.getIdsByNameCategoryAndState(name, category, state));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


}
