package com.example.roleservice.services;

import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    private RealmResource realm() {
        return keycloak.realm(realm);
    }

    public Map<String, Object> createUser(Map<String, Object> body) {
        String email = asString(body.get("email"));
        String username = asString(body.getOrDefault("username", email));
        String name = asString(body.getOrDefault("name", username));
        String password = asString(body.get("password")); // opcional si quieres setear

        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");

        UserRepresentation u = new UserRepresentation();
        u.setEmail(email);
        u.setUsername(username);
        u.setFirstName(name); // simple
        u.setEnabled(true);

        Response resp = realm().users().create(u);
        int status = resp.getStatus();

        if (status >= 300) {
            String msg = safeReadError(resp);
            throw new IllegalStateException("Keycloak create user failed: " + status + " - " + msg);
        }

        String userId = extractCreatedId(resp);
        if (userId == null) throw new IllegalStateException("User created but ID not returned");

        if (password != null && !password.isBlank()) {
            CredentialRepresentation cred = new CredentialRepresentation();
            cred.setType(CredentialRepresentation.PASSWORD);
            cred.setTemporary(false);
            cred.setValue(password);
            realm().users().get(userId).resetPassword(cred);
        }

        return Map.of("id", userId, "email", email, "username", username);
    }

    public List<Map<String, Object>> listUsers(int first, int max) {
        List<UserRepresentation> users = realm().users().list(first, max);
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserRepresentation u : users) {
            out.add(Map.of(
                    "id", u.getId(),
                    "username", u.getUsername(),
                    "email", u.getEmail(),
                    "enabled", u.isEnabled()
            ));
        }
        return out;
    }

    public Map<String, Object> getUser(String userId) {
        UserRepresentation u = realm().users().get(userId).toRepresentation();
        List<String> roles = realm().users().get(userId).roles().realmLevel().listAll()
                .stream().map(RoleRepresentation::getName).toList();

        return Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "email", u.getEmail(),
                "enabled", u.isEnabled(),
                "roles", roles
        );
    }

    public Map<String, Object> setEnabled(String userId, boolean value) {
        var users = realm().users().get(userId);
        UserRepresentation u = users.toRepresentation();
        u.setEnabled(value);
        users.update(u);
        return Map.of("id", userId, "enabled", value);
    }

    public Map<String, Object> addRealmRole(String userId, String roleName) {
        RoleRepresentation role = realm().roles().get(roleName).toRepresentation();
        realm().users().get(userId).roles().realmLevel().add(List.of(role));
        return Map.of("id", userId, "addedRole", roleName);
    }

    public Map<String, Object> removeRealmRole(String userId, String roleName) {
        RoleRepresentation role = realm().roles().get(roleName).toRepresentation();
        realm().users().get(userId).roles().realmLevel().remove(List.of(role));
        return Map.of("id", userId, "removedRole", roleName);
    }

    public Map<String, Object> resetPassword(String userId, String newPassword, boolean temporary) {
        if (newPassword == null || newPassword.isBlank()) throw new IllegalArgumentException("password is required");
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setTemporary(temporary);
        cred.setValue(newPassword);
        realm().users().get(userId).resetPassword(cred);
        return Map.of("id", userId, "passwordReset", true, "temporary", temporary);
    }

    // helpers
    private String asString(Object o) { return o == null ? null : o.toString(); }

    private String extractCreatedId(Response resp) {
        try {
            String location = resp.getHeaderString("Location");
            if (location == null) return null;
            return location.substring(location.lastIndexOf('/') + 1);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeReadError(Response resp) {
        try { return resp.readEntity(String.class); }
        catch (Exception e) { return ""; }
    }
}
