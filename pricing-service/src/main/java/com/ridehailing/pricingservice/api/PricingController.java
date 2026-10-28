package com.ridehailing.pricingservice.api;

import com.ridehailing.pricingservice.domain.FareCalculator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {
    private final Map<UUID, Map<String, Object>> quotes = new ConcurrentHashMap<>();

    public record QuoteRequest(
            @NotNull UUID customerId,
            @NotBlank String serviceType,
            @DecimalMin("-90.0") @DecimalMax("90.0") double pickupLat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double pickupLng,
            @DecimalMin("-90.0") @DecimalMax("90.0") double dropoffLat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double dropoffLng,
            @DecimalMin("0.0") BigDecimal distanceKm,
            @DecimalMin("0.0") BigDecimal durationMinutes,
            @DecimalMin("1.0") @DecimalMax("3.0") BigDecimal surge
    ) {}

    @PostMapping("/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> quote(@Valid @RequestBody QuoteRequest r) {
        BigDecimal surge = r.surge() == null ? BigDecimal.ONE : r.surge();
        BigDecimal baseFare = new BigDecimal("10000");
        BigDecimal perKm = new BigDecimal("5000");
        BigDecimal perMin = new BigDecimal("300");
        BigDecimal minimum = new BigDecimal("15000");
        BigDecimal total = FareCalculator.estimate(
                baseFare, r.distanceKm(), perKm, r.durationMinutes(), perMin, surge, minimum);

        UUID quoteId = UUID.randomUUID();
        Instant expires = Instant.now().plusSeconds(300);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("quoteId", quoteId.toString());
        q.put("customerId", r.customerId().toString());
        q.put("serviceType", r.serviceType());
        q.put("pickupLat", r.pickupLat());
        q.put("pickupLng", r.pickupLng());
        q.put("dropoffLat", r.dropoffLat());
        q.put("dropoffLng", r.dropoffLng());
        q.put("distanceKm", r.distanceKm());
        q.put("durationMinutes", r.durationMinutes());
        q.put("surge", surge);
        q.put("currency", "VND");
        q.put("baseFare", baseFare);
        q.put("perKm", perKm);
        q.put("perMinute", perMin);
        q.put("minimum", minimum);
        q.put("totalEstimate", total);
        q.put("ruleVersion", "1");
        q.put("expiresAt", expires.toString());
        q.put("status", "ACTIVE");
        quotes.put(quoteId, q);
        return q;
    }

    @GetMapping("/quotes/{quoteId}")
    public Map<String, Object> get(@PathVariable UUID quoteId) {
        Map<String, Object> q = quotes.get(quoteId);
        if (q == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found");
        return q;
    }

    /** FR-PRI-014/015: validate ownership, expiry, service type before trip create. */
    @PostMapping("/quotes/{quoteId}/validate")
    public Map<String, Object> validate(
            @PathVariable UUID quoteId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> q = get(quoteId);
        Instant exp = Instant.parse((String) q.get("expiresAt"));
        if (Instant.now().isAfter(exp)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Quote expired (FR-PRI-015)");
        }
        if (body != null && body.get("customerId") != null) {
            if (!String.valueOf(body.get("customerId")).equals(q.get("customerId"))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quote belongs to another customer (FR-PRI-014)");
            }
        }
        if (body != null && body.get("serviceType") != null) {
            if (!String.valueOf(body.get("serviceType")).equals(q.get("serviceType"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Service type mismatch");
            }
        }
        Map<String, Object> res = new LinkedHashMap<>(q);
        res.put("valid", true);
        return res;
    }
}
