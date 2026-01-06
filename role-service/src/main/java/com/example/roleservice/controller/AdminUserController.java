package com.example.roleservice.controller;


import com.example.roleservice.services.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/users")
@CrossOrigin("*")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService admin;

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(admin.createUser(body));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int first,
            @RequestParam(defaultValue = "20") int max
    ) {
        return ResponseEntity.ok(admin.listUsers(Math.max(first,0), Math.max(max,1)));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return ResponseEntity.ok(admin.getUser(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/{id}/enable")
    public ResponseEntity<?> enable(@PathVariable String id, @RequestParam boolean value) {
        return ResponseEntity.ok(admin.setEnabled(id, value));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/{id}/roles/{role}")
    public ResponseEntity<?> addRole(@PathVariable String id, @PathVariable String role) {
        return ResponseEntity.ok(admin.addRealmRole(id, role.toUpperCase()));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/{id}/roles/{role}")
    public ResponseEntity<?> removeRole(@PathVariable String id, @PathVariable String role) {
        return ResponseEntity.ok(admin.removeRealmRole(id, role.toUpperCase()));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(
            @PathVariable String id,
            @RequestBody Map<String, Object> body
    ) {
        String pass = body.get("password") == null ? null : body.get("password").toString();
        boolean temporary = body.get("temporary") instanceof Boolean b && b;
        return ResponseEntity.ok(admin.resetPassword(id, pass, temporary));
    }
}
