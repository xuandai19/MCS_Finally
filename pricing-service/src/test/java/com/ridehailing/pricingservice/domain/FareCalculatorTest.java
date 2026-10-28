package com.ridehailing.pricingservice.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class FareCalculatorTest {
    @Test
    void deterministicEstimateWithSurge() {
        BigDecimal total = FareCalculator.estimate(
                new BigDecimal("10000"),
                new BigDecimal("5.2"),
                new BigDecimal("5000"),
                new BigDecimal("18"),
                new BigDecimal("300"),
                new BigDecimal("1.2"),
                new BigDecimal("15000")
        );
        // (10000 + 5.2*5000 + 18*300) * 1.2 = (10000 + 26000 + 5400) * 1.2 = 41400 * 1.2 = 49680
        assertEquals(0, new BigDecimal("49680").compareTo(total));
    }

    @Test
    void appliesMinimum() {
        BigDecimal total = FareCalculator.estimate(
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal("15000"));
        assertEquals(0, new BigDecimal("15000").compareTo(total));
    }

    @Test
    void rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                FareCalculator.estimate(new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO));
    }
}
