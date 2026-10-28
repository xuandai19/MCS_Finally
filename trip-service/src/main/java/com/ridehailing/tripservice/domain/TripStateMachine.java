package com.ridehailing.tripservice.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TripStateMachine {
    private static final Map<TripStatus, Set<TripStatus>> ALLOWED = new EnumMap<>(TripStatus.class);

    static {
        ALLOWED.put(TripStatus.CREATED, EnumSet.of(TripStatus.MATCHING, TripStatus.CANCELLED));
        ALLOWED.put(TripStatus.MATCHING, EnumSet.of(TripStatus.ACCEPTED, TripStatus.CANCELLED, TripStatus.NO_DRIVER));
        ALLOWED.put(TripStatus.ACCEPTED, EnumSet.of(TripStatus.PICKING_UP, TripStatus.CANCELLED));
        ALLOWED.put(TripStatus.PICKING_UP, EnumSet.of(TripStatus.ARRIVED, TripStatus.CANCELLED));
        ALLOWED.put(TripStatus.ARRIVED, EnumSet.of(TripStatus.IN_TRIP, TripStatus.CANCELLED));
        ALLOWED.put(TripStatus.IN_TRIP, EnumSet.of(TripStatus.COMPLETED, TripStatus.CANCELLED));
        ALLOWED.put(TripStatus.COMPLETED, EnumSet.noneOf(TripStatus.class));
        ALLOWED.put(TripStatus.CANCELLED, EnumSet.noneOf(TripStatus.class));
        ALLOWED.put(TripStatus.NO_DRIVER, EnumSet.noneOf(TripStatus.class));
    }

    private TripStateMachine() {}

    public static boolean canTransition(TripStatus current, TripStatus target) {
        return ALLOWED.getOrDefault(current, EnumSet.noneOf(TripStatus.class)).contains(target);
    }

    public static TripStatus transition(TripStatus current, TripStatus target) {
        if (!canTransition(current, target)) {
            throw new IllegalStateException("Invalid transition " + current + " -> " + target);
        }
        return target;
    }
}
