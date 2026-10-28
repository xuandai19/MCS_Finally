package com.ridehailing.driverservice.service;

import com.ridehailing.driverservice.domain.DriverAvailability;
import com.ridehailing.driverservice.domain.DriverProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DriverServiceTest {
    DriverService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DriverService();
    }

    @Test
    void onboardReviewAndGoAvailable() {
        DriverProfile p = service.create(userId, "Nguyen Van A", "B2-1", "CAR", "30A-1");
        assertEquals("DRAFT", p.getReviewStatus().name());
        service.submitForReview(p.getDriverId());
        service.review(p.getDriverId(), "APPROVE", null, "reviewer-1");
        DriverProfile online = service.setAvailability(p.getDriverId(), DriverAvailability.AVAILABLE);
        assertEquals(DriverAvailability.AVAILABLE, online.getAvailability());
    }

    @Test
    void cannotGoAvailableWhenDraft() {
        DriverProfile p = service.create(userId, "A", "L", "CAR", "P");
        assertThrows(ResponseStatusException.class, () ->
                service.setAvailability(p.getDriverId(), DriverAvailability.AVAILABLE));
    }

    @Test
    void offeredBusyReleaseCycle() {
        DriverProfile p = service.create(userId, "A", "L", "CAR", "P");
        service.submitForReview(p.getDriverId());
        service.review(p.getDriverId(), "APPROVE", null, "r");
        service.setAvailability(p.getDriverId(), DriverAvailability.AVAILABLE);
        service.markOffered(p.getDriverId());
        assertEquals(DriverAvailability.OFFERED, service.get(p.getDriverId()).getAvailability());
        service.markBusy(p.getDriverId());
        assertEquals(DriverAvailability.BUSY, service.get(p.getDriverId()).getAvailability());
        assertThrows(ResponseStatusException.class, () ->
                service.setAvailability(p.getDriverId(), DriverAvailability.OFFLINE));
        service.releaseToAvailable(p.getDriverId());
        assertEquals(DriverAvailability.AVAILABLE, service.get(p.getDriverId()).getAvailability());
    }
}
