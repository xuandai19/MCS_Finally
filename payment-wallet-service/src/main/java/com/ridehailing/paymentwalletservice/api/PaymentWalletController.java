package com.ridehailing.paymentwalletservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class PaymentWalletController {

    private final Map<UUID, Map<String, Object>> payments = new ConcurrentHashMap<>();
    private final Map<String, UUID> paymentIdem = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> tripPayment = new ConcurrentHashMap<>(); // tripId -> paymentId
    private final Map<UUID, BigDecimal> walletBalance = new ConcurrentHashMap<>();
    private final Map<UUID, List<Map<String, Object>>> ledger = new ConcurrentHashMap<>();

    public record CreatePaymentRequest(
            @NotNull UUID tripId,
            @NotNull UUID customerId,
            @NotNull BigDecimal amount,
            @NotBlank String currency,
            @NotBlank String method
    ) {}

    public record ConfirmPaymentRequest(String providerReference) {}

    public record CreditWalletRequest(
            @NotNull UUID driverId,
            @NotNull UUID tripId,
            @NotNull BigDecimal grossAmount,
            @NotNull BigDecimal commissionAmount,
            @NotBlank String currency
    ) {}

    @PostMapping("/api/v1/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
            @Valid @RequestBody CreatePaymentRequest r) {
        if (r.amount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
        }
        if (idemKey != null && paymentIdem.containsKey(idemKey)) {
            return payments.get(paymentIdem.get(idemKey));
        }
        // one payment attempt identity per trip for happy path
        UUID existingPay = tripPayment.get(r.tripId());
        if (existingPay != null) {
            Map<String, Object> existing = payments.get(existingPay);
            if (existing != null && !"FAILED".equals(existing.get("status"))) {
                return existing;
            }
        }
        UUID id = UUID.randomUUID();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("paymentId", id.toString());
        p.put("tripId", r.tripId().toString());
        p.put("customerId", r.customerId().toString());
        p.put("amount", r.amount());
        p.put("currency", r.currency());
        p.put("method", r.method());
        p.put("status", "PENDING");
        p.put("providerReference", "");
        p.put("createdAt", Instant.now().toString());
        p.put("updatedAt", Instant.now().toString());
        payments.put(id, p);
        tripPayment.put(r.tripId(), id);
        if (idemKey != null) paymentIdem.put(idemKey, id);
        return p;
    }

    @GetMapping("/api/v1/payments/{paymentId}")
    public Map<String, Object> getPayment(@PathVariable UUID paymentId) {
        Map<String, Object> p = payments.get(paymentId);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        return p;
    }

    @PostMapping("/api/v1/payments/{paymentId}/confirm")
    public Map<String, Object> confirm(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) ConfirmPaymentRequest body) {
        Map<String, Object> p = getPayment(paymentId);
        synchronized (p) {
            String st = (String) p.get("status");
            if ("SUCCEEDED".equals(st)) return p; // idempotent
            if (!"PENDING".equals(st)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment not PENDING");
            }
            p.put("status", "SUCCEEDED");
            p.put("providerReference", body != null && body.providerReference() != null
                    ? body.providerReference() : "sandbox-" + paymentId);
            p.put("updatedAt", Instant.now().toString());
            p.put("confirmedAt", Instant.now().toString());
            return p;
        }
    }

    @PostMapping("/api/v1/wallets/credit")
    public Map<String, Object> credit(@Valid @RequestBody CreditWalletRequest r) {
        if (r.grossAmount().signum() < 0 || r.commissionAmount().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amounts must be non-negative");
        }
        List<Map<String, Object>> entries = ledger.computeIfAbsent(r.driverId(),
                k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (entries) {
            boolean exists = entries.stream().anyMatch(e ->
                    r.tripId().toString().equals(e.get("tripId")) && "TRIP_EARNING".equals(e.get("type")));
            if (exists) {
                return Map.of(
                        "driverId", r.driverId().toString(),
                        "balance", walletBalance.getOrDefault(r.driverId(), BigDecimal.ZERO),
                        "idempotent", true
                );
            }
            BigDecimal net = r.grossAmount().subtract(r.commissionAmount());
            if (net.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Net earning negative");
            }
            UUID entryId = UUID.randomUUID();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("entryId", entryId.toString());
            entry.put("walletId", r.driverId().toString());
            entry.put("type", "TRIP_EARNING");
            entry.put("amount", net);
            entry.put("currency", r.currency());
            entry.put("tripId", r.tripId().toString());
            entry.put("grossAmount", r.grossAmount());
            entry.put("commissionAmount", r.commissionAmount());
            entry.put("status", "POSTED");
            entry.put("createdAt", Instant.now().toString());
            entries.add(entry);
            walletBalance.merge(r.driverId(), net, BigDecimal::add);
            Map<String, Object> res = new LinkedHashMap<>(entry);
            res.put("balance", walletBalance.get(r.driverId()));
            res.put("idempotent", false);
            return res;
        }
    }

    @GetMapping("/api/v1/wallets/{driverId}")
    public Map<String, Object> wallet(@PathVariable UUID driverId) {
        return Map.of(
                "driverId", driverId.toString(),
                "balance", walletBalance.getOrDefault(driverId, BigDecimal.ZERO),
                "currency", "VND",
                "entries", ledger.getOrDefault(driverId, List.of())
        );
    }
}
