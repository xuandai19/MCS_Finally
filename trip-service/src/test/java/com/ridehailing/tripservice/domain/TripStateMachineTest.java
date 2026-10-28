package com.ridehailing.tripservice.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TripStateMachineTest {

    @Test
    void allowsHappyPath() {
        assertEquals(TripStatus.MATCHING, TripStateMachine.transition(TripStatus.CREATED, TripStatus.MATCHING));
        assertEquals(TripStatus.ACCEPTED, TripStateMachine.transition(TripStatus.MATCHING, TripStatus.ACCEPTED));
        assertEquals(TripStatus.PICKING_UP, TripStateMachine.transition(TripStatus.ACCEPTED, TripStatus.PICKING_UP));
        assertEquals(TripStatus.ARRIVED, TripStateMachine.transition(TripStatus.PICKING_UP, TripStatus.ARRIVED));
        assertEquals(TripStatus.IN_TRIP, TripStateMachine.transition(TripStatus.ARRIVED, TripStatus.IN_TRIP));
        assertEquals(TripStatus.COMPLETED, TripStateMachine.transition(TripStatus.IN_TRIP, TripStatus.COMPLETED));
    }

    @Test
    void allowsCancellationPaths() {
        assertTrue(TripStateMachine.canTransition(TripStatus.CREATED, TripStatus.CANCELLED));
        assertTrue(TripStateMachine.canTransition(TripStatus.MATCHING, TripStatus.CANCELLED));
        assertTrue(TripStateMachine.canTransition(TripStatus.ACCEPTED, TripStatus.CANCELLED));
        assertTrue(TripStateMachine.canTransition(TripStatus.PICKING_UP, TripStatus.CANCELLED));
        assertTrue(TripStateMachine.canTransition(TripStatus.ARRIVED, TripStatus.CANCELLED));
        assertTrue(TripStateMachine.canTransition(TripStatus.IN_TRIP, TripStatus.CANCELLED));
    }

    @Test
    void allowsNoDriver() {
        assertEquals(TripStatus.NO_DRIVER, TripStateMachine.transition(TripStatus.MATCHING, TripStatus.NO_DRIVER));
    }

    @Test
    void rejectsSkippingState() {
        assertThrows(IllegalStateException.class,
                () -> TripStateMachine.transition(TripStatus.MATCHING, TripStatus.COMPLETED));
        assertThrows(IllegalStateException.class,
                () -> TripStateMachine.transition(TripStatus.CREATED, TripStatus.ACCEPTED));
        assertThrows(IllegalStateException.class,
                () -> TripStateMachine.transition(TripStatus.ACCEPTED, TripStatus.IN_TRIP));
    }

    @Test
    void terminalsAreTerminal() {
        assertFalse(TripStateMachine.canTransition(TripStatus.COMPLETED, TripStatus.CANCELLED));
        assertFalse(TripStateMachine.canTransition(TripStatus.CANCELLED, TripStatus.MATCHING));
        assertFalse(TripStateMachine.canTransition(TripStatus.NO_DRIVER, TripStatus.ACCEPTED));
    }
}
