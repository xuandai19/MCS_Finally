package com.ridehailing.tripservice.api;

import com.ridehailing.tripservice.domain.TripStatus;
import com.ridehailing.tripservice.service.TripService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {
    private final TripService trips;

    public TripController(TripService trips) {
        this.trips = trips;
    }

    public record CreateTripRequest(
            @NotNull UUID customerId,
            @NotBlank String serviceType,
            UUID quoteId,
            BigDecimal totalEstimate,
            String currency,
            double pickupLat,
            double pickupLng,
            double dropoffLat,
            double dropoffLng
    ) {}

    public record TransitionRequest(@NotBlank String targetStatus, String actor, String reason) {}
    public record AcceptRequest(@NotNull UUID driverId) {}
    public record CancelRequest(String actor, String reason) {}
    public record CreateOffersRequest(@NotNull List<UUID> driverIds, Integer ttlSeconds) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
            @Valid @RequestBody CreateTripRequest r) {
        return trips.create(r.customerId(), r.serviceType(), r.quoteId(),
                r.totalEstimate() == null ? BigDecimal.ZERO : r.totalEstimate(),
                r.currency(), r.pickupLat(), r.pickupLng(), r.dropoffLat(), r.dropoffLng(), idemKey);
    }

    @GetMapping("/{tripId}")
    public Map<String, Object> get(@PathVariable UUID tripId) {
        return trips.get(tripId);
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID driverId) {
        if (customerId != null) return trips.byCustomer(customerId);
        if (driverId != null) return trips.byDriver(driverId);
        throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST, "customerId or driverId required");
    }

    @PostMapping("/{tripId}/transitions")
    public Map<String, Object> transition(@PathVariable UUID tripId, @Valid @RequestBody TransitionRequest r) {
        TripStatus target = TripStatus.valueOf(r.targetStatus().toUpperCase());
        return trips.transition(tripId, target, r.actor(), r.reason());
    }

    /** Backward-compatible direct accept (Postman E2E). Prefer offer-based accept. */
    @PostMapping("/{tripId}/accept")
    public Map<String, Object> accept(@PathVariable UUID tripId, @Valid @RequestBody AcceptRequest r) {
        return trips.acceptByDriver(tripId, r.driverId());
    }

    @PostMapping("/{tripId}/cancel")
    public Map<String, Object> cancel(@PathVariable UUID tripId, @Valid @RequestBody CancelRequest r) {
        return trips.cancel(tripId, r.actor(), r.reason());
    }

    @PostMapping("/{tripId}/no-driver")
    public Map<String, Object> noDriver(@PathVariable UUID tripId) {
        return trips.markNoDriver(tripId);
    }

    // ---- Matching / Offers (FR-MAT) ----

    @PostMapping("/{tripId}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, Object>> createOffers(
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateOffersRequest r) {
        return trips.createOffers(tripId, r.driverIds(), r.ttlSeconds() == null ? 30 : r.ttlSeconds());
    }

    @GetMapping("/{tripId}/offers")
    public List<Map<String, Object>> listOffers(@PathVariable UUID tripId) {
        return trips.listOffers(tripId);
    }

    @GetMapping("/offers/{offerId}")
    public Map<String, Object> getOffer(@PathVariable UUID offerId) {
        return trips.getOffer(offerId);
    }

    @PostMapping("/offers/{offerId}/accept")
    public Map<String, Object> acceptOffer(@PathVariable UUID offerId) {
        return trips.acceptOffer(offerId);
    }

    @PostMapping("/offers/{offerId}/reject")
    public Map<String, Object> rejectOffer(
            @PathVariable UUID offerId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return trips.rejectOffer(offerId, reason);
    }
}
