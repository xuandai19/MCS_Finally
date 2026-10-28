# Ride Hailing & Logistics — Backend Microservices (Expanded)

Backend theo `SRS_2_Ride_Hailing_Logistics.pdf`. Java 21, Spring Boot 3.4, Eureka, PostgreSQL (identity), Redis GEO (location), in-memory domain stores cho trip/driver/pricing/payment (demo).

## Luồng nghiệp vụ đã cover (quan trọng)

1. **Auth** — register CUSTOMER/DRIVER, login, refresh, logout (BCrypt + JWT access + hashed refresh).
2. **Driver onboard** — DRAFT → PENDING_REVIEW → APPROVED → AVAILABLE; eligibility FR-DRV-008; availability OFFLINE/AVAILABLE/OFFERED/BUSY.
3. **Location** — telemetry + sequence monotonic + Redis GEO nearby.
4. **Pricing** — quote với breakdown, surge, minimum, expiresAt; `POST /quotes/{id}/validate` (ownership + expiry).
5. **Trip + Matching/Offer**
   - Create → auto MATCHING; Idempotency-Key; 1 active trip / customer.
   - `POST /trips/{id}/offers` tạo offer PENDING (TTL).
   - `POST /trips/offers/{offerId}/accept` atomic (1 winner, cancel offers khác, lock driver).
   - Direct `POST /trips/{id}/accept` vẫn hỗ trợ (tương thích Postman cũ).
   - State machine đầy đủ + statusHistory; cancel idempotent; NO_DRIVER.
   - 1 active trip / driver (CON-06).
6. **Driver coordination** — mark-offered / mark-busy / release.
7. **Payment + Wallet** — payment PENDING→SUCCEEDED idempotent; 1 payment / trip; wallet ledger TRIP_EARNING idempotent theo tripId.

## Chưa làm (cố ý / TBD)

- WebSocket realtime fan-out
- Full JWT filter trên mọi service + RBAC endpoint
- Matching engine tự động gọi Location (hiện client/orchestrator gửi candidate driverIds)
- Kafka outbox, map provider, payment provider thật
- Persistence Postgres cho trip/driver/payment (hiện in-memory)

## Start

```bash
docker compose -f infra/docker-compose.yml up -d
# Start services (ports): discovery 8761, identity 8081, driver 8082,
# location 8083, pricing 8084, trip 8085, payment 8086, gateway 8080
```

Postman: `Ride-Hailing-Expanded.postman_collection.json`

## Unit tests

```bash
cd trip-service && ./gradlew test
cd driver-service && ./gradlew test
cd pricing-service && ./gradlew test
```

Cover: TripStateMachine, TripService (offer atomic, dual-trip guard, lifecycle), FareCalculator, DriverService availability cycle.
