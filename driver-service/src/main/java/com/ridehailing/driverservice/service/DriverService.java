package com.ridehailing.driverservice.service;

import com.ridehailing.driverservice.api.DriverEligibility;
import com.ridehailing.driverservice.domain.DriverAvailability;
import com.ridehailing.driverservice.domain.DriverProfile;
import com.ridehailing.driverservice.domain.DriverReviewStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DriverService {
    private final Map<UUID, DriverProfile> byId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byUser = new ConcurrentHashMap<>();

    public DriverProfile create(UUID userId, String fullName, String licenseNumber,
                                String vehicleType, String plateNumber) {
        if (byUser.containsKey(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver profile already exists for user");
        }
        DriverProfile p = new DriverProfile(userId, fullName, licenseNumber, vehicleType, plateNumber);
        byId.put(p.getDriverId(), p);
        byUser.put(userId, p.getDriverId());
        return p;
    }

    public DriverProfile get(UUID driverId) {
        DriverProfile p = byId.get(driverId);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found");
        return p;
    }

    public DriverProfile getByUser(UUID userId) {
        UUID id = byUser.get(userId);
        if (id == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found for user");
        return get(id);
    }

    public DriverProfile submitForReview(UUID driverId) {
        DriverProfile p = get(driverId);
        if (p.getReviewStatus() != DriverReviewStatus.DRAFT && p.getReviewStatus() != DriverReviewStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT/REJECTED can submit");
        }
        p.setReviewStatus(DriverReviewStatus.PENDING_REVIEW);
        return p;
    }

    public DriverProfile review(UUID driverId, String decision, String reason, String reviewerId) {
        DriverProfile p = get(driverId);
        if (p.getReviewStatus() != DriverReviewStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not PENDING_REVIEW");
        }
        String d = decision == null ? "" : decision.toUpperCase();
        if ("APPROVE".equals(d) || "APPROVED".equals(d)) {
            p.setReviewStatus(DriverReviewStatus.APPROVED);
            p.setRejectReason(null);
        } else if ("REJECT".equals(d) || "REJECTED".equals(d)) {
            p.setReviewStatus(DriverReviewStatus.REJECTED);
            p.setRejectReason(reason == null ? "Rejected" : reason);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be APPROVE or REJECT");
        }
        p.setReviewerId(reviewerId);
        p.setReviewedAt(java.time.Instant.now());
        return p;
    }

    /**
     * Public availability change (driver app). FR-DRV-008..011.
     * Only OFFLINE <-> AVAILABLE when eligible.
     */
    public DriverProfile setAvailability(UUID driverId, DriverAvailability target) {
        DriverProfile p = get(driverId);
        synchronized (p) {
            if (target == DriverAvailability.AVAILABLE) {
                if (!DriverEligibility.canGoOnline(p.getReviewStatus(), p.isAccountActive(),
                        p.isDocumentsValid(), p.isVehicleValid())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Driver not eligible to go AVAILABLE (FR-DRV-008)");
                }
                if (p.getAvailability() == DriverAvailability.BUSY
                        || p.getAvailability() == DriverAvailability.OFFERED) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Cannot go AVAILABLE while OFFERED/BUSY");
                }
            }
            if (target == DriverAvailability.OFFLINE) {
                if (p.getAvailability() == DriverAvailability.BUSY) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Cannot go Offline during active trip (FR-DRV-011)");
                }
            }
            if (target != DriverAvailability.OFFLINE && target != DriverAvailability.AVAILABLE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Driver app may only set OFFLINE or AVAILABLE; use internal endpoints for OFFERED/BUSY");
            }
            p.setAvailability(target);
            return p;
        }
    }

    /** FR-DRV-012: reserve driver for offer (AVAILABLE -> OFFERED). */
    public DriverProfile markOffered(UUID driverId) {
        DriverProfile p = get(driverId);
        synchronized (p) {
            if (p.getAvailability() != DriverAvailability.AVAILABLE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver not AVAILABLE");
            }
            p.setAvailability(DriverAvailability.OFFERED);
            return p;
        }
    }

    /** FR-MAT-014 / FR-DRV-013: accept -> BUSY. */
    public DriverProfile markBusy(UUID driverId) {
        DriverProfile p = get(driverId);
        synchronized (p) {
            if (p.getAvailability() != DriverAvailability.OFFERED
                    && p.getAvailability() != DriverAvailability.AVAILABLE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Driver must be OFFERED or AVAILABLE to become BUSY");
            }
            p.setAvailability(DriverAvailability.BUSY);
            return p;
        }
    }

    /** After COMPLETED/CANCELLED: BUSY/OFFERED -> AVAILABLE if still eligible. */
    public DriverProfile releaseToAvailable(UUID driverId) {
        DriverProfile p = get(driverId);
        synchronized (p) {
            if (p.getAvailability() == DriverAvailability.BUSY
                    || p.getAvailability() == DriverAvailability.OFFERED) {
                if (DriverEligibility.canGoOnline(p.getReviewStatus(), p.isAccountActive(),
                        p.isDocumentsValid(), p.isVehicleValid())) {
                    p.setAvailability(DriverAvailability.AVAILABLE);
                } else {
                    p.setAvailability(DriverAvailability.OFFLINE);
                }
            }
            return p;
        }
    }

    public List<DriverProfile> listAvailable() {
        return byId.values().stream()
                .filter(p -> p.getAvailability() == DriverAvailability.AVAILABLE)
                .filter(p -> DriverEligibility.canGoOnline(p.getReviewStatus(), p.isAccountActive(),
                        p.isDocumentsValid(), p.isVehicleValid()))
                .toList();
    }

    public Map<String, Object> toMap(DriverProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("driverId", p.getDriverId().toString());
        m.put("userId", p.getUserId().toString());
        m.put("fullName", p.getFullName());
        m.put("licenseNumber", p.getLicenseNumber());
        m.put("vehicleType", p.getVehicleType());
        m.put("plateNumber", p.getPlateNumber());
        m.put("reviewStatus", p.getReviewStatus().name());
        m.put("availability", p.getAvailability().name());
        m.put("documentsValid", p.isDocumentsValid());
        m.put("vehicleValid", p.isVehicleValid());
        m.put("accountActive", p.isAccountActive());
        m.put("rejectReason", p.getRejectReason() == null ? "" : p.getRejectReason());
        m.put("reviewerId", p.getReviewerId() == null ? "" : p.getReviewerId());
        m.put("updatedAt", p.getUpdatedAt().toString());
        return m;
    }
}
