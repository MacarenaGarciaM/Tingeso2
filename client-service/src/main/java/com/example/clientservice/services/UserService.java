package com.example.clientservice.services;

import com.example.clientservice.entities.UserEntity;
import com.example.clientservice.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private RestTemplate restTemplate;

    @Value("${services.loan.base-url:http://loan-service}")
    private String loanBaseUrl;

    // ========= PROVISIONING DESDE JWT =========
    public UserEntity provisionFromJwt(Jwt jwt) {
        String kcId  = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String name  = jwt.getClaimAsString("name");
        if (name == null) name = jwt.getClaimAsString("preferred_username");

        boolean isAdmin;
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
            isAdmin = roles.contains("admin") || roles.contains("ADMIN");
        } else {
            isAdmin = false;
        }

        String rutClaim   = jwt.getClaimAsString("rut");
        String phoneClaim = jwt.getClaimAsString("phone");

        String finalName = name;

        return userRepository.findByKeycloakId(kcId).map(u -> {
            u.setEmail(email);
            u.setName(finalName);
            u.setAdmin(isAdmin);

            if (rutClaim != null && !rutClaim.isBlank() && u.getRut() == null) {
                String normalized = normalizeRut(rutClaim);
                UserEntity other = userRepository.findByRut(normalized);
                if (other != null && !other.getId().equals(u.getId())) {
                    throw new IllegalArgumentException("RUT ya registrado por otro usuario");
                }
                u.setRut(normalized);
            }

            if (phoneClaim != null && !phoneClaim.isBlank()) {
                Integer phoneParsed = tryParsePhone(phoneClaim);
                if (phoneParsed != null) u.setPhone(phoneParsed);
            }

            return userRepository.save(u);

        }).orElseGet(() -> {

            UserEntity existingByEmail = userRepository.findByEmail(email);
            if (existingByEmail != null &&
                    (existingByEmail.getKeycloakId() == null || existingByEmail.getKeycloakId().isBlank())) {

                existingByEmail.setKeycloakId(kcId);
                existingByEmail.setName(finalName);
                existingByEmail.setAdmin(isAdmin);

                if (rutClaim != null && !rutClaim.isBlank() && existingByEmail.getRut() == null) {
                    String normalized = normalizeRut(rutClaim);
                    UserEntity other = userRepository.findByRut(normalized);
                    if (other != null && !other.getId().equals(existingByEmail.getId())) {
                        throw new IllegalArgumentException("RUT ya registrado por otro usuario");
                    }
                    existingByEmail.setRut(normalized);
                }

                if (phoneClaim != null && !phoneClaim.isBlank()) {
                    Integer phoneParsed = tryParsePhone(phoneClaim);
                    if (phoneParsed != null) existingByEmail.setPhone(phoneParsed);
                }

                return userRepository.save(existingByEmail);
            }

            UserEntity u = new UserEntity();
            u.setKeycloakId(kcId);
            u.setEmail(email);
            u.setName(finalName);
            u.setAdmin(isAdmin);
            u.setActive(true);
            u.setAmountOfLoans(0);

            if (rutClaim != null && !rutClaim.isBlank()) {
                String normalized = normalizeRut(rutClaim);
                if (userRepository.findByRut(normalized) != null) {
                    throw new IllegalArgumentException("RUT ya registrado por otro usuario");
                }
                u.setRut(normalized);
            }

            if (phoneClaim != null && !phoneClaim.isBlank()) {
                Integer phoneParsed = tryParsePhone(phoneClaim);
                if (phoneParsed != null) u.setPhone(phoneParsed);
            }

            return userRepository.save(u);
        });
    }

    // ========= CREACIÓN MANUAL =========
    public UserEntity saveUser(UserEntity user) {
        UserEntity existingUserEmail = userRepository.findByEmail(user.getEmail());
        if (existingUserEmail != null) throw new IllegalArgumentException("User with this email already exists.");

        String normalizedRut = (user.getRut() == null) ? null : normalizeRut(user.getRut());

        // ✅ OJO: aquí estaba tu bug (rechazabas cualquier rut no nulo)
        if (normalizedRut == null || normalizedRut.isBlank()) {
            throw new IllegalArgumentException("RUT inválido");
        }

        UserEntity existingUserRut = userRepository.findByRut(normalizedRut);
        if (existingUserRut != null) throw new IllegalArgumentException("User with this RUT already exists.");

        UserEntity newUser = new UserEntity(
                null,
                user.getKeycloakId(),
                user.getName(),
                user.getEmail(),
                null,
                normalizedRut,
                user.getPhone(),
                user.isAdmin(),
                true,
                0
        );
        return userRepository.save(newUser);
    }

    public UserEntity updateActive(UserEntity user, boolean active) {
        user.setActive(active);
        return userRepository.save(user);
    }

    public List<UserEntity> getAllUsers() { return userRepository.findAll(); }
    public UserEntity getUserById(Long id) { return userRepository.findById(id).orElse(null); }
    public UserEntity getUserByRut(String rut) { return userRepository.findByRut(rut == null ? null : normalizeRut(rut)); }

    // ========= RECOMPUTE ACTIVE (ahora via loan-service) =========
    public UserEntity recomputeActiveStatus(String rutUser) {
        String normalizedRut = rutUser == null ? null : normalizeRut(rutUser);
        UserEntity u = userRepository.findByRut(normalizedRut);
        if (u == null) return null;

        boolean hasOverdue = callLoanBoolean("/loan/exists/overdue?rut=" + normalizedRut);
        boolean hasUnpaidLateFine = callLoanBoolean("/loan/exists/unpaid-latefine?rut=" + normalizedRut);
        boolean hasUnpaidDamage = callLoanBoolean("/loan/exists/unpaid-damage?rut=" + normalizedRut);

        boolean shouldBeActive = !(hasOverdue || hasUnpaidLateFine || hasUnpaidDamage);
        u.setActive(shouldBeActive);
        return userRepository.save(u);
    }

    private boolean callLoanBoolean(String pathAndQuery) {
        try {
            String url = loanBaseUrl + pathAndQuery;
            Boolean resp = restTemplate.getForObject(url, Boolean.class);
            return resp != null && resp;
        } catch (RestClientException ex) {
            // si loan-service está abajo, por seguridad puedes decidir:
            // return true;  (bloquear)
            // o return false; (no bloquear)
            // yo recomiendo NO bloquear por fallo de red:
            return false;
        }
    }

    // ========= HELPERS =========
    private String normalizeRut(String rut) {
        if (rut == null) return null;
        String raw = rut.replace(".", "").replace(" ", "").toUpperCase();
        if (!raw.contains("-")) {
            if (raw.length() < 2) return raw;
            raw = raw.substring(0, raw.length() - 1) + "-" + raw.substring(raw.length() - 1);
        }
        return raw;
    }

    private Integer tryParsePhone(String phone) {
        try {
            String digits = phone.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return null;
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
