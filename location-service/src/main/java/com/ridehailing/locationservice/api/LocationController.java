package com.ridehailing.locationservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private static final Duration LOCATION_TTL = Duration.ofSeconds(60);
    private static final String GEO_KEY = "drivers:geo";

    private final StringRedisTemplate redis;

    public LocationController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record Telemetry(
            @NotNull UUID driverId,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @Positive double accuracy,
            @NotNull Instant deviceTimestamp,
            @PositiveOrZero long sequence
    ) {}

    @PostMapping("/telemetry")
    public Map<String, Object> update(@Valid @RequestBody Telemetry t) {
        String key = "driver:location:" + t.driverId();
        String existing = redis.opsForValue().get(key);
        if (existing != null) {
            String[] parts = existing.split(",", 5);
            if (parts.length >= 4) {
                try {
                    long prevSeq = Long.parseLong(parts[3]);
                    if (t.sequence() < prevSeq) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Stale telemetry sequence rejected (A-09)");
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        Instant serverTime = Instant.now();
        String value = t.longitude() + "," + t.latitude() + "," + t.accuracy() + "," + t.sequence() + "," + serverTime;
        redis.opsForValue().set(key, value, LOCATION_TTL);
        redis.opsForGeo().add(GEO_KEY, new Point(t.longitude(), t.latitude()), t.driverId().toString());
        return Map.of("accepted", true, "sequence", t.sequence(), "serverTime", serverTime.toString());
    }

    @GetMapping("/{driverId}")
    public Map<String, Object> latest(@PathVariable UUID driverId) {
        String value = redis.opsForValue().get("driver:location:" + driverId);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found or expired");
        }
        return Map.of("driverId", driverId.toString(), "location", value);
    }

    /** FR-LOC-009 / FR-MAT-001: query drivers within radius km */
    @GetMapping("/nearby")
    public List<Map<String, Object>> nearby(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "3") double radiusKm) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid coordinates");
        }
        var results = redis.opsForGeo().radius(
                GEO_KEY,
                new Circle(
                        new Point(longitude, latitude),
                        new Distance(radiusKm, Metrics.KILOMETERS)
                ),
                RedisGeoCommands.GeoRadiusCommandArgs
                        .newGeoRadiusArgs()
                        .includeDistance()
                        .includeCoordinates()
                        .sortAscending()
        );
        if (results == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        results.forEach(r -> {
            String driverId = r.getContent().getName();
            String loc = redis.opsForValue().get("driver:location:" + driverId);
            if (loc == null) return; // expired TTL
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("driverId", driverId);
            m.put("distanceKm", r.getDistance() == null ? null : r.getDistance().getValue());
            m.put("location", loc);
            out.add(m);
        });
        return out;
    }
}
