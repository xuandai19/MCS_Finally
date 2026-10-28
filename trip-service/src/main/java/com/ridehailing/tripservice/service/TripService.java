package com.ridehailing.tripservice.service;

import com.ridehailing.tripservice.domain.TripStateMachine;
import com.ridehailing.tripservice.domain.TripStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trip + matching/offer orchestration (in-memory).
 * Covers FR-TRIP-001..015 (core), FR-MAT-007..015 (offer model), FR-CAN-001/005/006.
 */
@Service
public class TripService {
    private final Map<UUID, Map<String, Object>> trips = new ConcurrentHashMap<>();
    private final Map<String, UUID> idempotency = new ConcurrentHashMap<>();
    /** offerId -> offer map */
    private final Map<UUID, Map<String, Object>> offers = new ConcurrentHashMap<>();
    /** driverId -> active tripId (MATCHING accepted through IN_TRIP) */
    private final Map<UUID, UUID> driverActiveTrip = new ConcurrentHashMap<>();

    public Map<String, Object> create(UUID customerId, String serviceType, UUID quoteId,
                                      BigDecimal totalEstimate, String currency,
                                      double pickupLat, double pickupLng,
                                      double dropoffLat, double dropoffLng,
                                      String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            UUID existing = idempotency.get(idempotencyKey);
            if (existing != null) {
                return trips.get(existing);
            }
        }
        // FR-TRIP-007: one active trip per customer
        boolean hasActive = trips.values().stream().anyMatch(t ->
                customerId.toString().equals(t.get("customerId"))
                        && !Set.of("COMPLETED", "CANCELLED", "NO_DRIVER").contains(String.valueOf(t.get("status"))));
        if (hasActive) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer already has an active trip (FR-TRIP-007)");
        }
        if (totalEstimate == null || totalEstimate.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "totalEstimate must be positive");
        }

        UUID tripId = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("tripId", tripId.toString());
        t.put("customerId", customerId.toString());
        t.put("serviceType", serviceType == null ? "RIDE" : serviceType);
        t.put("quoteId", quoteId == null ? "" : quoteId.toString());
        t.put("totalEstimate", totalEstimate);
        t.put("currency", currency == null ? "VND" : currency);
        t.put("pickupLat", pickupLat);
        t.put("pickupLng", pickupLng);
        t.put("dropoffLat", dropoffLat);
        t.put("dropoffLng", dropoffLng);
        t.put("status", TripStatus.CREATED.name());
        t.put("driverId", "");
        t.put("cancelReason", "");
        t.put("lastActor", "customer");
        t.put("statusHistory", new ArrayList<Map<String, Object>>());
        t.put("offerIds", new ArrayList<String>());
        t.put("createdAt", now.toString());
        t.put("updatedAt", now.toString());
        trips.put(tripId, t);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.put(idempotencyKey, tripId);
        }
        appendHistory(t, TripStatus.CREATED, TripStatus.MATCHING, "system", "auto-start matching");
        t.put("status", TripStatus.MATCHING.name());
        t.put("updatedAt", Instant.now().toString());
        return t;
    }

    public Map<String, Object> get(UUID tripId) {
        Map<String, Object> t = trips.get(tripId);
        if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        return t;
    }

    @SuppressWarnings("unchecked")
    private void appendHistory(Map<String, Object> t, TripStatus from, TripStatus to, String actor, String reason) {
        List<Map<String, Object>> hist = (List<Map<String, Object>>) t.computeIfAbsent("statusHistory",
                k -> new ArrayList<>());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("from", from == null ? null : from.name());
        entry.put("to", to.name());
        entry.put("actor", actor == null ? "system" : actor);
        entry.put("reason", reason == null ? "" : reason);
        entry.put("at", Instant.now().toString());
        hist.add(entry);
    }

    public Map<String, Object> transition(UUID tripId, TripStatus target, String actor, String reason) {
        Map<String, Object> t = get(tripId);
        synchronized (t) {
            TripStatus current = TripStatus.valueOf((String) t.get("status"));
            try {
                TripStateMachine.transition(current, target);
            } catch (IllegalStateException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
            }
            appendHistory(t, current, target, actor, reason);
            t.put("status", target.name());
            t.put("updatedAt", Instant.now().toString());
            if (reason != null) t.put("cancelReason", reason);
            if (actor != null) t.put("lastActor", actor);

            if (target == TripStatus.COMPLETED || target == TripStatus.CANCELLED || target == TripStatus.NO_DRIVER) {
                releaseDriver(t);
            }
            return t;
        }
    }

    private void releaseDriver(Map<String, Object> t) {
        String did = (String) t.get("driverId");
        if (did != null && !did.isBlank()) {
            try {
                driverActiveTrip.remove(UUID.fromString(did), UUID.fromString((String) t.get("tripId")));
            } catch (Exception ignored) {}
        }
        // expire open offers
        @SuppressWarnings("unchecked")
        List<String> offerIds = (List<String>) t.getOrDefault("offerIds", List.of());
        for (String oid : offerIds) {
            try {
                Map<String, Object> o = offers.get(UUID.fromString(oid));
                if (o != null && "PENDING".equals(o.get("status"))) {
                    o.put("status", "CANCELLED");
                    o.put("updatedAt", Instant.now().toString());
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * FR-MAT-007..009: create offers for candidate drivers while trip is MATCHING.
     * Drivers already BUSY on another trip are rejected.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> createOffers(UUID tripId, List<UUID> driverIds, int ttlSeconds) {
        Map<String, Object> t = get(tripId);
        synchronized (t) {
            TripStatus current = TripStatus.valueOf((String) t.get("status"));
            if (current != TripStatus.MATCHING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Offers only allowed in MATCHING");
            }
            if (driverIds == null || driverIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "driverIds required");
            }
            int ttl = ttlSeconds <= 0 ? 30 : Math.min(ttlSeconds, 120);
            Instant expires = Instant.now().plusSeconds(ttl);
            List<Map<String, Object>> created = new ArrayList<>();
            List<String> offerIds = (List<String>) t.computeIfAbsent("offerIds", k -> new ArrayList<>());

            for (UUID driverId : driverIds) {
                // FR-MAT-005 / CON-06: driver already on active trip
                UUID other = driverActiveTrip.get(driverId);
                if (other != null && !other.equals(tripId)) {
                    continue; // skip busy driver
                }
                UUID offerId = UUID.randomUUID();
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("offerId", offerId.toString());
                o.put("tripId", tripId.toString());
                o.put("driverId", driverId.toString());
                o.put("status", "PENDING"); // PENDING | ACCEPTED | REJECTED | EXPIRED | CANCELLED
                o.put("expiresAt", expires.toString());
                o.put("createdAt", Instant.now().toString());
                o.put("updatedAt", Instant.now().toString());
                offers.put(offerId, o);
                offerIds.add(offerId.toString());
                created.add(o);
            }
            t.put("updatedAt", Instant.now().toString());
            return created;
        }
    }

    public Map<String, Object> getOffer(UUID offerId) {
        Map<String, Object> o = offers.get(offerId);
        if (o == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found");
        return o;
    }

    public List<Map<String, Object>> listOffers(UUID tripId) {
        get(tripId); // ensure exists
        return offers.values().stream()
                .filter(o -> tripId.toString().equals(o.get("tripId")))
                .sorted(Comparator.comparing(o -> (String) o.get("createdAt")))
                .toList();
    }

    /**
     * FR-MAT-010..014: atomic accept — only one winner; reject/expire others; lock driver.
     */
    public Map<String, Object> acceptOffer(UUID offerId) {
        Map<String, Object> o = getOffer(offerId);
        UUID tripId = UUID.fromString((String) o.get("tripId"));
        UUID driverId = UUID.fromString((String) o.get("driverId"));
        Map<String, Object> t = get(tripId);

        synchronized (t) {
            // expire check
            Instant exp = Instant.parse((String) o.get("expiresAt"));
            if (Instant.now().isAfter(exp) && "PENDING".equals(o.get("status"))) {
                o.put("status", "EXPIRED");
                o.put("updatedAt", Instant.now().toString());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer expired");
            }
            if (!"PENDING".equals(o.get("status"))) {
                // idempotent if already ACCEPTED by same
                if ("ACCEPTED".equals(o.get("status")) && driverId.toString().equals(t.get("driverId"))) {
                    return t;
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Offer not PENDING: " + o.get("status"));
            }
            TripStatus current = TripStatus.valueOf((String) t.get("status"));
            if (current != TripStatus.MATCHING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip not in MATCHING");
            }
            if (!((String) t.get("driverId")).isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip already accepted by another driver");
            }
            // CON-06 / FR-MAT-011: driver cannot hold two active trips
            UUID existing = driverActiveTrip.putIfAbsent(driverId, tripId);
            if (existing != null && !existing.equals(tripId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver already has an active trip");
            }

            o.put("status", "ACCEPTED");
            o.put("updatedAt", Instant.now().toString());
            t.put("driverId", driverId.toString());
            appendHistory(t, TripStatus.MATCHING, TripStatus.ACCEPTED, "driver", "offer accepted");
            t.put("status", TripStatus.ACCEPTED.name());
            t.put("updatedAt", Instant.now().toString());
            t.put("lastActor", "driver");

            // cancel other pending offers for this trip
            for (Map<String, Object> other : offers.values()) {
                if (tripId.toString().equals(other.get("tripId"))
                        && !"ACCEPTED".equals(other.get("status"))
                        && "PENDING".equals(other.get("status"))) {
                    other.put("status", "CANCELLED");
                    other.put("updatedAt", Instant.now().toString());
                }
            }
            return t;
        }
    }

    /** Legacy accept by driverId (creates implicit offer if needed for backward compat with Postman). */
    public Map<String, Object> acceptByDriver(UUID tripId, UUID driverId) {
        Map<String, Object> t = get(tripId);
        synchronized (t) {
            TripStatus current = TripStatus.valueOf((String) t.get("status"));
            if (current != TripStatus.MATCHING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip not in MATCHING");
            }
            if (!((String) t.get("driverId")).isBlank()) {
                if (driverId.toString().equals(t.get("driverId"))) {
                    return t; // idempotent
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip already accepted");
            }
            UUID existing = driverActiveTrip.putIfAbsent(driverId, tripId);
            if (existing != null && !existing.equals(tripId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver already has an active trip");
            }
            // auto-create accepted offer for audit
            UUID offerId = UUID.randomUUID();
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("offerId", offerId.toString());
            o.put("tripId", tripId.toString());
            o.put("driverId", driverId.toString());
            o.put("status", "ACCEPTED");
            o.put("expiresAt", Instant.now().plusSeconds(30).toString());
            o.put("createdAt", Instant.now().toString());
            o.put("updatedAt", Instant.now().toString());
            offers.put(offerId, o);
            @SuppressWarnings("unchecked")
            List<String> offerIds = (List<String>) t.computeIfAbsent("offerIds", k -> new ArrayList<>());
            offerIds.add(offerId.toString());

            t.put("driverId", driverId.toString());
            appendHistory(t, TripStatus.MATCHING, TripStatus.ACCEPTED, "driver", "direct accept");
            t.put("status", TripStatus.ACCEPTED.name());
            t.put("updatedAt", Instant.now().toString());
            t.put("lastActor", "driver");
            return t;
        }
    }

    public Map<String, Object> rejectOffer(UUID offerId, String reason) {
        Map<String, Object> o = getOffer(offerId);
        if (!"PENDING".equals(o.get("status"))) {
            return o; // idempotent
        }
        o.put("status", "REJECTED");
        o.put("reason", reason == null ? "" : reason);
        o.put("updatedAt", Instant.now().toString());
        return o;
    }

    /** FR-MAT-015: no driver found */
    public Map<String, Object> markNoDriver(UUID tripId) {
        return transition(tripId, TripStatus.NO_DRIVER, "system", "no candidates within limit");
    }

    public Map<String, Object> cancel(UUID tripId, String actor, String reason) {
        Map<String, Object> t = get(tripId);
        synchronized (t) {
            TripStatus current = TripStatus.valueOf((String) t.get("status"));
            if (current == TripStatus.COMPLETED || current == TripStatus.CANCELLED || current == TripStatus.NO_DRIVER) {
                return t; // FR-CAN-006 idempotent
            }
            return transition(tripId, TripStatus.CANCELLED, actor == null ? "customer" : actor,
                    reason == null ? "CANCELLED" : reason);
        }
    }

    public List<Map<String, Object>> byCustomer(UUID customerId) {
        return trips.values().stream()
                .filter(t -> customerId.toString().equals(t.get("customerId")))
                .sorted(Comparator.comparing(t -> (String) t.get("createdAt"), Comparator.reverseOrder()))
                .toList();
    }

    public List<Map<String, Object>> byDriver(UUID driverId) {
        return trips.values().stream()
                .filter(t -> driverId.toString().equals(t.get("driverId")))
                .sorted(Comparator.comparing(t -> (String) t.get("createdAt"), Comparator.reverseOrder()))
                .toList();
    }
}
