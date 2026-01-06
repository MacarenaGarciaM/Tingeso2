package com.example.roleservice.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        return Map.of(
                "name", auth.getName(),
                "authorities", auth.getAuthorities()
        );
    }
}
