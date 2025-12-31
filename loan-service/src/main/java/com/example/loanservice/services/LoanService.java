package com.example.loanservice.services;

import com.example.loanservice.entities.LoanEntity;
import com.example.loanservice.entities.LoanItemEntity;
import com.example.loanservice.repositories.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;
    private final RestTemplate restTemplate;

    // ServiceId en Eureka (usa @LoadBalanced en RestTemplate)
    @Value("${services.inventory.base-url:http://inventory-service}")
    private String inventoryBaseUrl;

    @Value("${settings.daily-rent-price:2500}")
    private int dailyRentPrice;

    @Value("${features.check-user-active:false}")
    private boolean checkUserActive;

    @Value("${services.user.base-url:http://user-service}")
    private String userBaseUrl;

    // =========================
    // CREATE LOAN
    // =========================
    @Transactional
    public LoanEntity createLoan(
            String rutUser,
            LocalDate reservationDate,
            LocalDate returnDate,
            List<Item> items
    ) {
        if (reservationDate == null || returnDate == null)
            throw new IllegalArgumentException("Reservation and return dates are required.");
        if (returnDate.isBefore(reservationDate))
            throw new IllegalArgumentException("Return date cannot be before reservation date.");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("At least one item is required.");
        if (rutUser == null || rutUser.isBlank())
            throw new IllegalArgumentException("rutUser is required.");

        // (Opcional) Validar usuario activo
        if (checkUserActive) {
            Boolean active = fetchUserActive(rutUser);
            if (active != null && !active) {
                throw new IllegalArgumentException("User is inactive due to overdue loans or unpaid fines.");
            }
        }

        // Max 5 active loans
        long activeCount = loanRepository.countByRutUserAndLateReturnDateIsNull(rutUser);
        if (activeCount >= 5)
            throw new IllegalArgumentException("User already has 5 active loans.");

        LoanEntity loan = new LoanEntity();
        loan.setRutUser(rutUser);
        loan.setReservationDate(reservationDate);
        loan.setReturnDate(returnDate);
        loan.setLateReturnDate(null);
        loan.setLateFine(0);
        loan.setDamagePenalty(0);

        loan.setTotal(calculateLoanTotal(reservationDate, returnDate));

        Set<Long> seen = new HashSet<>();

        for (Item it : items) {
            if (it == null || it.toolId == null)
                throw new IllegalArgumentException("Each item requires 'toolId'.");

            if (!seen.add(it.toolId))
                throw new IllegalArgumentException("Tool repeated in the same loan: " + it.toolId);

            int qty = (it.quantity == null) ? 1 : it.quantity;
            if (qty <= 0) throw new IllegalArgumentException("quantity must be >= 1");
            if (qty != 1) throw new IllegalArgumentException("Only one unit per tool is allowed.");

            // 1) Consultar tool en inventory (Map)
            Map<String, Object> disponibleTool = fetchToolMap(it.toolId);
            if (disponibleTool == null)
                throw new IllegalArgumentException("Tool not found (id=" + it.toolId + ")");

            String initialState = asString(disponibleTool.get("initialState"));
            int amount = asInt(disponibleTool.get("amount"), 0);
            String name = asString(disponibleTool.get("name"));
            String category = asString(disponibleTool.get("category"));

            if (!"Disponible".equalsIgnoreCase(initialState))
                throw new IllegalArgumentException("Tool id=" + it.toolId + " is not 'Disponible'.");

            if (amount < qty)
                throw new IllegalArgumentException("Not enough stock for tool id=" + it.toolId +
                        ". Available: " + amount);

            // 2) Validación: mismo usuario no puede tener préstamo activo de misma herramienta (name+category)
            List<Long> prestadaIds = fetchIdsByNameCategoryState(name, category, "Prestada");

            if (!prestadaIds.isEmpty()) {
                boolean alreadyActive = loanRepository.existsActiveWithAnyToolId(rutUser, prestadaIds);
                if (alreadyActive) {
                    throw new IllegalArgumentException(
                            "El usuario ya tiene un préstamo activo de esta herramienta (" +
                                    name + " - " + category + ")."
                    );
                }
            }

            // 3) Cambiar estado en inventory: Disponible -> Prestada
            moveToolState(it.toolId, "Prestada", rutUser);

            // 4) Guardar loan_item con toolId + snapshot
            LoanItemEntity line = new LoanItemEntity();
            line.setToolId(it.toolId);
            line.setToolNameSnapshot(name);
            loan.addItem(line);
        }

        return loanRepository.save(loan);
    }

    // =========================
    // RETURN LOAN
    // =========================
    @Transactional
    public LoanEntity returnLoan(
            Long loanId,
            LocalDate actualReturnDate,
            Set<Long> damagedToolIds,
            Set<Long> irreparableToolIds,
            Integer finePerDay,
            Map<Long, Integer> repairCosts
    ) {
        if (actualReturnDate == null) throw new IllegalArgumentException("actualReturnDate is required.");

        LoanEntity loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        if (loan.getLateReturnDate() != null)
            throw new IllegalArgumentException("Loan is already returned (closed).");

        damagedToolIds     = (damagedToolIds == null) ? Collections.emptySet() : damagedToolIds;
        irreparableToolIds = (irreparableToolIds == null) ? Collections.emptySet() : irreparableToolIds;
        repairCosts        = (repairCosts == null) ? Collections.emptyMap() : repairCosts;

        // no superposición
        Set<Long> inter = new HashSet<>(damagedToolIds);
        inter.retainAll(irreparableToolIds);
        if (!inter.isEmpty())
            throw new IllegalArgumentException("A tool cannot be both damaged and irreparable: " + inter);

        // validar que toolIds pertenecen al préstamo
        Set<Long> loanToolIds = new HashSet<>();
        for (LoanItemEntity li : loan.getItems()) loanToolIds.add(li.getToolId());

        if (!loanToolIds.containsAll(damagedToolIds)) {
            Set<Long> unknown = new HashSet<>(damagedToolIds); unknown.removeAll(loanToolIds);
            throw new IllegalArgumentException("Damaged IDs not in this loan: " + unknown);
        }
        if (!loanToolIds.containsAll(irreparableToolIds)) {
            Set<Long> unknown = new HashSet<>(irreparableToolIds); unknown.removeAll(loanToolIds);
            throw new IllegalArgumentException("Irreparable IDs not in this loan: " + unknown);
        }

        int damagePenalty = 0;

        for (LoanItemEntity line : loan.getItems()) {
            Long toolId = line.getToolId();

            Map<String, Object> toolMap = fetchToolMap(toolId);
            if (toolMap == null)
                throw new IllegalArgumentException("Tool not found in inventory (id=" + toolId + ")");

            int repositionValue = asInt(toolMap.get("repositionValue"), 0);

            if (irreparableToolIds.contains(toolId)) {
                damagePenalty += Math.max(0, repositionValue);
                moveToolState(toolId, "Dada de baja", loan.getRutUser());

            } else if (damagedToolIds.contains(toolId)) {
                int repair = Math.max(0, Optional.ofNullable(repairCosts.get(toolId)).orElse(0));
                damagePenalty += repair;
                moveToolState(toolId, "En reparación", loan.getRutUser());

            } else {
                moveToolState(toolId, "Disponible", loan.getRutUser());
            }
        }

        int fineRate = (finePerDay == null) ? 0 : Math.max(0, finePerDay);
        long lateDays = Math.max(0, ChronoUnit.DAYS.between(loan.getReturnDate(), actualReturnDate));
        int lateFine = (int) (lateDays * (long) fineRate);

        loan.setLateReturnDate(actualReturnDate);
        loan.setLateFine(lateFine);
        loan.setDamagePenalty(damagePenalty);
        if (lateFine > 0) loan.setLateFinePaid(false);
        if (damagePenalty > 0) loan.setDamagePenaltyPaid(false);

        LoanEntity saved = loanRepository.save(loan);

        if (checkUserActive) {
            recomputeUserActive(loan.getRutUser());
        }
        return saved;
    }

    // =========================
    // PAY FINES
    // =========================
    @Transactional
    public LoanEntity payFines(Long loanId, boolean payLateFine, boolean payDamagePenalty) {
        LoanEntity loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        if (payLateFine && loan.getLateFine() > 0) loan.setLateFinePaid(true);
        if (payDamagePenalty && loan.getDamagePenalty() > 0) loan.setDamagePenaltyPaid(true);

        LoanEntity saved = loanRepository.save(loan);

        if (checkUserActive) {
            recomputeUserActive(loan.getRutUser());
        }
        return saved;
    }

    // =========================
    // LISTADOS
    // =========================
    public List<LoanEntity> listActiveLoans(String rutUser) {
        return loanRepository.findByRutUserAndLateReturnDateIsNull(rutUser);
    }

    public List<LoanEntity> listAllActiveLoans() {
        return loanRepository.findByLateReturnDateIsNull();
    }

    public Page<LoanEntity> listLoansWithUnpaidDebts(String rutUser,
                                                     LocalDate start,
                                                     LocalDate end,
                                                     Pageable pageable) {
        String rut = (rutUser != null && rutUser.isBlank()) ? null : rutUser;
        boolean hasStart = (start != null);
        boolean hasEnd   = (end != null);

        return loanRepository.findLoansWithUnpaidDebts(
                rut,
                hasStart, start,
                hasEnd,   end,
                pageable
        );
    }

    public Page<LoanEntity> listOverdueLoans(String rutUser, Pageable pageable) {
        LocalDate today = LocalDate.now();
        if (rutUser == null || rutUser.isBlank()) {
            return loanRepository.findByLateReturnDateIsNullAndReturnDateBefore(today, pageable);
        }
        return loanRepository.findByRutUserAndLateReturnDateIsNullAndReturnDateBefore(rutUser, today, pageable);
    }

    // =========================
    // Helpers
    // =========================
    private int calculateLoanTotal(LocalDate reservationDate, LocalDate returnDate) {
        long days = ChronoUnit.DAYS.between(reservationDate, returnDate);
        if (days < 1) days = 1;
        return (int) (days * (long) dailyRentPrice);
    }

    // =========================
    // Inventory calls (SIN DTO)
    // =========================
    private void moveToolState(Long toolId, String newState, String rutUser) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString(inventoryBaseUrl)
                    .path("/tool/{id}")
                    .queryParam("state", newState)
                    .queryParam("rutUser", rutUser)
                    .buildAndExpand(toolId)
                    .toUriString();

            restTemplate.put(url, null);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Inventory-service no respondió al cambiar estado (toolId=" + toolId + ")", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchToolMap(Long toolId) {
        try {
            String url = inventoryBaseUrl + "/tool/" + toolId;
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return resp.getBody();
        } catch (RestClientException ex) {
            return null;
        }
    }

    private List<Long> fetchIdsByNameCategoryState(String name, String category, String state) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString(inventoryBaseUrl)
                    .path("/tool/ids")
                    .queryParam("name", name)
                    .queryParam("category", category)
                    .queryParam("state", state)
                    .build()
                    .toUriString();

            ResponseEntity<List> resp = restTemplate.getForEntity(url, List.class);
            List<?> raw = resp.getBody();
            if (raw == null) return List.of();

            List<Long> out = new ArrayList<>();
            for (Object o : raw) {
                out.add(asLong(o));
            }
            return out;
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    // =========================
    // User calls (opcionales)
    // =========================
    private Boolean fetchUserActive(String rutUser) {
        try {
            String url = userBaseUrl + "/user/" + rutUser + "/active";
            return restTemplate.getForObject(url, Boolean.class);
        } catch (RestClientException ex) {
            return null;
        }
    }

    private void recomputeUserActive(String rutUser) {
        try {
            String url = userBaseUrl + "/user/" + rutUser + "/recompute-active";
            restTemplate.postForEntity(url, null, Void.class);
        } catch (RestClientException ignored) {}
    }

    // =========================
    // Converters seguros (Map -> types)
    // =========================
    private String asString(Object v) {
        return (v == null) ? null : String.valueOf(v);
    }

    private int asInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return Math.toIntExact(l);
        if (v instanceof Double d) return d.intValue();
        if (v instanceof Float f) return f.intValue();
        if (v instanceof BigDecimal bd) return bd.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof BigDecimal bd) return bd.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    // Body para creación (esto NO es DTO de dominio; es el body del request)
    public static class Item {
        public Long toolId;
        public Integer quantity;
        public Item() {}
    }
}
