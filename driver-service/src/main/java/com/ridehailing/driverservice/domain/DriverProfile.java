package com.ridehailing.driverservice.domain;

import java.time.Instant;
import java.util.UUID;

public class DriverProfile {
    private final UUID driverId;
    private final UUID userId;
    private String fullName;
    private String licenseNumber;
    private String vehicleType;
    private String plateNumber;
    private DriverReviewStatus reviewStatus = DriverReviewStatus.DRAFT;
    private DriverAvailability availability = DriverAvailability.OFFLINE;
    private boolean documentsValid = true;
    private boolean vehicleValid = true;
    private boolean accountActive = true;
    private String rejectReason;
    private String reviewerId;
    private Instant reviewedAt;
    private Instant updatedAt = Instant.now();

    public DriverProfile(UUID userId, String fullName, String licenseNumber, String vehicleType, String plateNumber) {
        this.driverId = UUID.randomUUID();
        this.userId = userId;
        this.fullName = fullName;
        this.licenseNumber = licenseNumber;
        this.vehicleType = vehicleType;
        this.plateNumber = plateNumber;
    }

    public UUID getDriverId() { return driverId; }
    public UUID getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getLicenseNumber() { return licenseNumber; }
    public String getVehicleType() { return vehicleType; }
    public String getPlateNumber() { return plateNumber; }
    public DriverReviewStatus getReviewStatus() { return reviewStatus; }
    public DriverAvailability getAvailability() { return availability; }
    public boolean isDocumentsValid() { return documentsValid; }
    public boolean isVehicleValid() { return vehicleValid; }
    public boolean isAccountActive() { return accountActive; }
    public String getRejectReason() { return rejectReason; }
    public String getReviewerId() { return reviewerId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFullName(String v) { this.fullName = v; touch(); }
    public void setLicenseNumber(String v) { this.licenseNumber = v; touch(); }
    public void setVehicleType(String v) { this.vehicleType = v; touch(); }
    public void setPlateNumber(String v) { this.plateNumber = v; touch(); }
    public void setReviewStatus(DriverReviewStatus s) { this.reviewStatus = s; touch(); }
    public void setAvailability(DriverAvailability a) { this.availability = a; touch(); }
    public void setDocumentsValid(boolean v) { this.documentsValid = v; touch(); }
    public void setVehicleValid(boolean v) { this.vehicleValid = v; touch(); }
    public void setAccountActive(boolean v) { this.accountActive = v; touch(); }
    public void setRejectReason(String v) { this.rejectReason = v; touch(); }
    public void setReviewerId(String v) { this.reviewerId = v; touch(); }
    public void setReviewedAt(Instant v) { this.reviewedAt = v; touch(); }
    private void touch() { this.updatedAt = Instant.now(); }
}
