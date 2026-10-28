package com.ridehailing.driverservice.api;
import com.ridehailing.driverservice.domain.DriverReviewStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DriverEligibilityTest {
 @Test void onlyApprovedAndValidDriverCanGoOnline(){
  assertTrue(DriverEligibility.canGoOnline(DriverReviewStatus.APPROVED,true,true,true));
  assertFalse(DriverEligibility.canGoOnline(DriverReviewStatus.PENDING_REVIEW,true,true,true));
  assertFalse(DriverEligibility.canGoOnline(DriverReviewStatus.APPROVED,true,false,true));
 }
}
