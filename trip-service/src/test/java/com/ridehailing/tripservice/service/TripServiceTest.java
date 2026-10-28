package com.ridehailing.tripservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TripServiceTest {
    TripService service;
    UUID customer = UUID.randomUUID();
    UUID driver1 = UUID.randomUUID();
    UUID driver2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TripService();
    }

    @Test
    void createStartsInMatchingAndIdempotent() {
        Map<String, Object> t1 = service.create(customer, "RIDE", UUID.randomUUID(),
                new BigDecimal("50000"), "VND", 21.0, 105.8, 21.1, 105.9, "key-1");
        assertEquals("MATCHING", t1.get("status"));
        Map<String, Object> t2 = service.create(customer, "RIDE", UUID.randomUUID(),
                new BigDecimal("50000"), "VND", 21.0, 105.8, 21.1, 105.9, "key-1");
        assertEquals(t1.get("tripId"), t2.get("tripId"));
    }

    @Test
    void blocksSecondActiveTripForCustomer() {
        service.create(customer, "RIDE", null, new BigDecimal("10000"), "VND",
                21, 105, 21.1, 105.1, "a");
        assertThrows(ResponseStatusException.class, () ->
                service.create(customer, "RIDE", null, new BigDecimal("10000"), "VND",
                        21, 105, 21.1, 105.1, "b"));
    }

    @Test
    void offerAcceptIsAtomicOneWinner() {
        Map<String, Object> trip = service.create(customer, "RIDE", null, new BigDecimal("20000"), "VND",
                21, 105, 21.1, 105.1, null);
        UUID tripId = UUID.fromString((String) trip.get("tripId"));
        List<Map<String, Object>> offers = service.createOffers(tripId, List.of(driver1, driver2), 60);
        assertEquals(2, offers.size());
        UUID offer1 = UUID.fromString((String) offers.get(0).get("offerId"));
        UUID offer2 = UUID.fromString((String) offers.get(1).get("offerId"));

        Map<String, Object> accepted = service.acceptOffer(offer1);
        assertEquals("ACCEPTED", accepted.get("status"));
        assertEquals(driver1.toString(), accepted.get("driverId"));

        assertThrows(ResponseStatusException.class, () -> service.acceptOffer(offer2));
        Map<String, Object> o2 = service.getOffer(offer2);
        assertEquals("CANCELLED", o2.get("status"));
    }

    @Test
    void driverCannotAcceptTwoTrips() {
        Map<String, Object> t1 = service.create(customer, "RIDE", null, new BigDecimal("10000"), "VND",
                21, 105, 21.1, 105.1, null);
        UUID trip1 = UUID.fromString((String) t1.get("tripId"));
        service.acceptByDriver(trip1, driver1);

        UUID customer2 = UUID.randomUUID();
        Map<String, Object> t2 = service.create(customer2, "RIDE", null, new BigDecimal("10000"), "VND",
                21, 105, 21.1, 105.1, null);
        UUID trip2 = UUID.fromString((String) t2.get("tripId"));
        assertThrows(ResponseStatusException.class, () -> service.acceptByDriver(trip2, driver1));
    }

    @Test
    void cancelIsIdempotentAndReleasesFlow() {
        Map<String, Object> trip = service.create(customer, "RIDE", null, new BigDecimal("10000"), "VND",
                21, 105, 21.1, 105.1, null);
        UUID tripId = UUID.fromString((String) trip.get("tripId"));
        Map<String, Object> c1 = service.cancel(tripId, "customer", "CHANGED_MIND");
        assertEquals("CANCELLED", c1.get("status"));
        Map<String, Object> c2 = service.cancel(tripId, "customer", "CHANGED_MIND");
        assertEquals("CANCELLED", c2.get("status"));
    }

    @Test
    void fullLifecycleHappyPath() {
        Map<String, Object> trip = service.create(customer, "RIDE", null, new BigDecimal("30000"), "VND",
                21, 105, 21.1, 105.1, null);
        UUID tripId = UUID.fromString((String) trip.get("tripId"));
        service.acceptByDriver(tripId, driver1);
        service.transition(tripId, com.ridehailing.tripservice.domain.TripStatus.PICKING_UP, "driver", null);
        service.transition(tripId, com.ridehailing.tripservice.domain.TripStatus.ARRIVED, "driver", null);
        service.transition(tripId, com.ridehailing.tripservice.domain.TripStatus.IN_TRIP, "driver", null);
        Map<String, Object> done = service.transition(tripId, com.ridehailing.tripservice.domain.TripStatus.COMPLETED, "system", null);
        assertEquals("COMPLETED", done.get("status"));
        assertNotNull(done.get("statusHistory"));
        @SuppressWarnings("unchecked")
        List<?> hist = (List<?>) done.get("statusHistory");
        assertTrue(hist.size() >= 5);
    }

    @Test
    void noDriverFromMatching() {
        Map<String, Object> trip = service.create(customer, "RIDE", null, new BigDecimal("10000"), "VND",
                21, 105, 21.1, 105.1, null);
        UUID tripId = UUID.fromString((String) trip.get("tripId"));
        Map<String, Object> nd = service.markNoDriver(tripId);
        assertEquals("NO_DRIVER", nd.get("status"));
    }
}
