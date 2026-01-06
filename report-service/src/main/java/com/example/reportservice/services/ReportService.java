package com.example.reportservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final RestTemplate restTemplate;

    @Value("${services.loan.base-url:http://loan-service}")
    private String loanBaseUrl;

    @Value("${services.inventory.base-url:http://inventory-service}")
    private String inventoryBaseUrl;

    @Value("${services.client.base-url:http://client-service}")
    private String clientBaseUrl;

    // ----------------------------
    // RF6.1: préstamos activos + estado (vigente/atrasado)
    // ----------------------------
    public List<Map<String, Object>> activeLoans(LocalDate start, LocalDate end) {
        List<Map<String, Object>> loans = fetchActiveLoansFromLoanService();

        LocalDate today = LocalDate.now();

        return loans.stream()
                .filter(l -> inRange(dateOf(l, "reservationDate"), start, end))
                .map(l -> {
                    LocalDate returnDate = dateOf(l, "returnDate");
                    boolean overdue = (returnDate != null && returnDate.isBefore(today));
                    Map<String, Object> out = new LinkedHashMap<>(l);
                    out.put("status", overdue ? "atrasado" : "vigente");
                    return out;
                })
                .collect(Collectors.toList());
    }

    // ----------------------------
    // RF6.2: clientes con atrasos
    // ----------------------------
    public List<Map<String, Object>> overdueClients(LocalDate start, LocalDate end) {
        List<Map<String, Object>> loans = fetchActiveLoansFromLoanService();

        LocalDate today = LocalDate.now();

        // filtrar préstamos atrasados y en rango
        List<Map<String, Object>> overdueLoans = loans.stream()
                .filter(l -> inRange(dateOf(l, "reservationDate"), start, end))
                .filter(l -> {
                    LocalDate returnDate = dateOf(l, "returnDate");
                    return returnDate != null && returnDate.isBefore(today);
                })
                .toList();

        // agrupar por rutUser
        Map<String, Long> overdueCountByRut = overdueLoans.stream()
                .map(l -> (String) l.get("rutUser"))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()));

        List<Map<String, Object>> result = new ArrayList<>();

        for (var e : overdueCountByRut.entrySet()) {
            String rut = e.getKey();
            long count = e.getValue();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rutUser", rut);
            row.put("overdueLoans", count);

            // opcional: enriquecer con client-service
            Map<String, Object> client = fetchClientByRut(rut);
            if (client != null) {
                row.put("client", client); // name/email/etc.
            }

            result.add(row);
        }

        // ordena por más atrasos
        result.sort((a,b) -> Long.compare(
                ((Number)b.get("overdueLoans")).longValue(),
                ((Number)a.get("overdueLoans")).longValue()
        ));

        return result;
    }

    // ----------------------------
    // RF6.3: ranking herramientas más prestadas
    // ----------------------------
    public List<Map<String, Object>> topTools(LocalDate start, LocalDate end, int limit) {
        List<Map<String, Object>> loans = fetchLoansInRangeFromLoanService(start, end);

        // contar toolId en items
        Map<Long, Long> countByTool = new HashMap<>();

        for (Map<String, Object> loan : loans) {
            Object itemsObj = loan.get("items");
            if (!(itemsObj instanceof List<?> items)) continue;

            for (Object itObj : items) {
                if (!(itObj instanceof Map<?,?> it)) continue;
                Object toolIdObj = it.get("toolId");
                if (!(toolIdObj instanceof Number n)) continue;

                Long toolId = n.longValue();
                countByTool.put(toolId, countByTool.getOrDefault(toolId, 0L) + 1);
            }
        }

        // ordenar y limitar
        List<Map.Entry<Long, Long>> top = countByTool.entrySet().stream()
                .sorted((a,b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, limit))
                .toList();

        List<Map<String, Object>> out = new ArrayList<>();

        for (var e : top) {
            Long toolId = e.getKey();
            Long times = e.getValue();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("toolId", toolId);
            row.put("times", times);

            // enriquecer con inventory
            Map<String, Object> tool = fetchToolById(toolId);
            if (tool != null) {
                row.put("tool", tool); // name/category/...
            }

            out.add(row);
        }

        return out;
    }

    // ==========================================================
    // Helpers HTTP (loan/client/inventory)
    // ==========================================================

    private List<Map<String, Object>> fetchActiveLoansFromLoanService() {
        try {
            // Ideal: que loan-service exponga GET /loans/active
            String url = loanBaseUrl + "/loans/active";
            Map[] arr = restTemplate.getForObject(url, Map[].class);
            return (arr == null) ? List.of() : Arrays.asList(arr);
        } catch (RestClientException ex) {
            throw new IllegalStateException("No pude consultar loan-service (/loans/active).", ex);
        }
    }

    private List<Map<String, Object>> fetchLoansInRangeFromLoanService(LocalDate start, LocalDate end) {
        try {
            // Ideal: GET /loans?start=...&end=...
            // Si NO lo tienes, puedes por mientras llamar /loans/all y filtrar acá.
            String url = loanBaseUrl + "/loans/all";
            Map[] arr = restTemplate.getForObject(url, Map[].class);
            List<Map<String, Object>> list = (arr == null) ? List.of() : Arrays.asList(arr);

            return list.stream()
                    .filter(l -> inRange(dateOf(l, "reservationDate"), start, end))
                    .toList();
        } catch (RestClientException ex) {
            throw new IllegalStateException("No pude consultar loan-service (/loans/all).", ex);
        }
    }

    private Map<String, Object> fetchToolById(Long toolId) {
        try {
            String url = inventoryBaseUrl + "/tool/" + toolId;
            return restTemplate.getForObject(url, Map.class);
        } catch (RestClientException ex) {
            return null;
        }
    }

    private Map<String, Object> fetchClientByRut(String rut) {
        try {
            String url = clientBaseUrl + "/users/rut/" + rut;
            return restTemplate.getForObject(url, Map.class);
        } catch (RestClientException ex) {
            return null;
        }
    }

    // ==========================================================
    // Helpers fechas
    // ==========================================================

    private boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
        if (date == null) return true; // si no hay fecha, no filtramos por fecha
        if (start != null && date.isBefore(start)) return false;
        if (end != null && date.isAfter(end)) return false;
        return true;
    }

    private LocalDate dateOf(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        // si Jackson lo parsea como String (yyyy-MM-dd)
        if (v instanceof String s) return LocalDate.parse(s);
        return null;
    }
}
