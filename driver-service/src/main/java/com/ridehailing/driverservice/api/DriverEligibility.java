package com.ridehailing.driverservice.api;

import com.ridehailing.driverservice.domain.DriverReviewStatus;

public final class DriverEligibility {
    private DriverEligibility() {}

    public static boolean canGoOnline(DriverReviewStatus reviewStatus, boolean accountActive,
                                      boolean documentsValid, boolean vehicleValid) {
        return reviewStatus == DriverReviewStatus.APPROVED && accountActive && documentsValid && vehicleValid;
    }
}
