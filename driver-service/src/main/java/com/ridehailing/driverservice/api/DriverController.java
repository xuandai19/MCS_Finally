package com.ridehailing.driverservice.api;

import com.ridehailing.driverservice.domain.DriverAvailability;
import com.ridehailing.driverservice.domain.DriverProfile;
import com.ridehailing.driverservice.service.DriverService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {
    private final DriverService drivers;

    public DriverController(DriverService drivers) {
        this.drivers = drivers;
    }

    public record CreateDriverRequest(
            @NotNull UUID userId,
            @NotBlank String fullName,
            @NotBlank String licenseNumber,
            @NotBlank String vehicleType,
            @NotBlank String plateNumber
    ) {}

    public record ReviewRequest(@NotBlank String decision, String reason, String reviewerId) {}
    public record AvailabilityRequest(@NotBlank String availability) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody CreateDriverRequest r) {
        return drivers.toMap(drivers.create(r.userId(), r.fullName(), r.licenseNumber(), r.vehicleType(), r.plateNumber()));
    }

    @GetMapping("/{driverId}")
    public Map<String, Object> get(@PathVariable UUID driverId) {
        return drivers.toMap(drivers.get(driverId));
    }

    @GetMapping("/by-user/{userId}")
    public Map<String, Object> byUser(@PathVariable UUID userId) {
        return drivers.toMap(drivers.getByUser(userId));
    }

    @PostMapping("/{driverId}/submit")
    public Map<String, Object> submit(@PathVariable UUID driverId) {
        return drivers.toMap(drivers.submitForReview(driverId));
    }

    @PostMapping("/{driverId}/review")
    public Map<String, Object> review(@PathVariable UUID driverId, @Valid @RequestBody ReviewRequest r) {
        return drivers.toMap(drivers.review(driverId, r.decision(), r.reason(),
                r.reviewerId() == null ? "system" : r.reviewerId()));
    }

    @PostMapping("/{driverId}/availability")
    public Map<String, Object> availability(@PathVariable UUID driverId, @Valid @RequestBody AvailabilityRequest r) {
        DriverAvailability a = DriverAvailability.valueOf(r.availability().toUpperCase());
        return drivers.toMap(drivers.setAvailability(driverId, a));
    }

    @GetMapping("/available")
    public List<Map<String, Object>> listAvailable() {
        return drivers.listAvailable().stream().map(drivers::toMap).toList();
    }

    /** Internal matching coordination */
    @PostMapping("/{driverId}/mark-offered")
    public Map<String, Object> markOffered(@PathVariable UUID driverId) {
        return drivers.toMap(drivers.markOffered(driverId));
    }

    @PostMapping("/{driverId}/mark-busy")
    public Map<String, Object> markBusy(@PathVariable UUID driverId) {
        return drivers.toMap(drivers.markBusy(driverId));
    }

    @PostMapping("/{driverId}/release")
    public Map<String, Object> release(@PathVariable UUID driverId) {
        return drivers.toMap(drivers.releaseToAvailable(driverId));
    }
}
