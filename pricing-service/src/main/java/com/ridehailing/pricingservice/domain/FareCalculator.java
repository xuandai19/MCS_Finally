package com.ridehailing.pricingservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FareCalculator {
    private FareCalculator() {}

    public static BigDecimal estimate(BigDecimal baseFare, BigDecimal distanceKm, BigDecimal perKm,
                                      BigDecimal durationMinutes, BigDecimal perMinute,
                                      BigDecimal surge, BigDecimal minimum) {
        if (baseFare.signum() < 0 || distanceKm.signum() < 0 || perKm.signum() < 0
                || durationMinutes.signum() < 0 || perMinute.signum() < 0 || surge.signum() < 0) {
            throw new IllegalArgumentException("Fare inputs must be non-negative");
        }
        BigDecimal raw = baseFare
                .add(distanceKm.multiply(perKm))
                .add(durationMinutes.multiply(perMinute))
                .multiply(surge);
        return raw.max(minimum).setScale(0, RoundingMode.HALF_UP);
    }
}
