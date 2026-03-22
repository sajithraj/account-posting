# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Account Posting Orchestrator — accepts posting requests from upstream systems, delivers to a core banking orchestrator, tracks every response leg, and provides a UI with retry.

## Three Project Directories

| Directory | What it is | Port |
|-----------|-----------|------|
| `account-posting/` | Spring Boot 3.2, Java 21, Maven | 8080 |
| `db/` | Flyway migration scripts + Maven plugin | — |
| `ui/` | React 18, TypeScript, Vite | 3000 |

---

## `account-posting/`

### Run Commands

```bash
# Local dev
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Build
mvn clean package -DskipTests

# Tests
mvn test
mvn test -Dtest=ClassName
```

### Package Structure

```
com.accountposting
├── AccountPostingApplication.java
├── config/
│   └── JpaConfig.java                    @EnableJpaAuditing + @EnableJpaRepositories
├── common/
│   ├── entity/BaseEntity.java            createdAt / updatedAt via JPA auditing
│   ├── response/ApiResponse.java         Generic { success, data, error, timestamp } envelope
│   ├── response/ApiError.java            Error detail + field-level validation errors
│   └── exception/
│       ├── GlobalExceptionHandler.java   404 / 422 / 400 / 500 mapped to ApiResponse
│       ├── ResourceNotFoundException     → HTTP 404
│       └── BusinessException             → HTTP 422, carries error code
├── posting/                              account_posting table
│   ├── entity/    AccountPosting, AccountPostingRequestPayload, AccountPostingResponsePayload
│   │              CreditDebitIndicator (enum), PostingStatus (enum)
│   ├── repository/ AccountPostingRepository (JpaSpecificationExecutor)
│   │               AccountPostingSpecification — builds JPA Criteria predicates dynamically
│   ├── dto/       AccountPostingRequest, AccountPostingResponse, LegResponse
│   │              AccountPostingSearchRequest, RetryRequest, RetryResponse
│   ├── mapper/    AccountPostingMapper — entity↔DTO + AccountPostingLegResponse→LegResponse
│   ├── service/   AccountPostingService + Impl
│   └── controller/ AccountPostingController
├── leg/                                  account_posting_leg table
│   ├── entity/    AccountPostingLeg (@Version for optimistic lock), LegStatus (enum)
│   ├── repository/ AccountPostingLegRepository
│   │               lockForRetry() — atomic @Modifying JPQL, single UPDATE statement
│   ├── dto/       AccountPostingLegRequest, AccountPostingLegResponse
│   │              UpdateLegRequest, RetryLockRequest
│   ├── mapper/    AccountPostingLegMapper
│   ├── service/   AccountPostingLegService + Impl
│   └── controller/ AccountPostingLegController
└── client/
    ├── CoreBankingClient.java            *** STUB — replace with real integration ***
    └── dto/  CoreBankingLegResult, CoreBankingResponse
```

### Key Flows

**POST /account-posting:**
1. Idempotency guard on `endToEndReferenceId`
2. Save `AccountPosting` (PENDING) + `AccountPostingRequestPayload`
3. `CoreBankingClient.submit()` stub
4. For each leg in response → `legService.addLeg()` (in-process, no HTTP)
5. Update status (SUCCESS/FAILED) + save `AccountPostingResponsePayload`

**POST /account-posting/retry:**
1. Resolve target postingIds (all non-SUCCESS if none supplied)
2. `legService.lockLegsForRetry()` — single atomic `@Modifying` UPDATE acquires the pessimistic lock
3. For each locked leg → `CoreBankingClient.submitSingleLeg()` stub → `legService.updateLeg()`
4. Update parent posting status

**GET /account-posting (search):**
`AccountPostingSpecification.from(criteria)` composes JPA Criteria predicates — every field is optional.

### Design Notes

- **Payload tables** use `@MapsId` — same PK as parent row, 1:1, keeps `account_posting` lightweight.
- **Leg decoupling** — `AccountPostingLeg.postingId` is a plain `Long` column, not a JPA `@ManyToOne`. The `leg` package never imports from `posting`.
- **Retry lock** — `retry_locked_until TIMESTAMPTZ` + single `@Modifying` JPQL in `lockForRetry()`. `@Version` adds optimistic locking at the JPA layer on top.
- **No HTTP between posting and leg** — both packages are in the same process; `AccountPostingServiceImpl` injects `AccountPostingLegService` directly.

---

## `db/`

### Run Commands

```bash
cd db

# Apply pending migrations
mvn flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/account_posting_db \
  -Dflyway.user=postgres -Dflyway.password=postgres

# Or set credentials in db/flyway.conf and just run:
mvn flyway:migrate

mvn flyway:info        # migration status
mvn flyway:validate    # verify applied scripts match files
mvn flyway:repair      # fix checksum mismatches
```

### Migration Files

Scripts live in `db/src/main/resources/db/migration/`.
Run migrations via the `db/` module before starting the Spring Boot service. The app has no Flyway dependency — it connects to an already-migrated database.

Naming: `V{number}__{description}.sql` — never edit an already-applied migration.

---

## `ui/`

### Run Commands

```bash
cd ui
npm install       # first time only
npm run dev       # dev server on port 3000
npm run build     # production build
npm run preview   # preview production build locally
```

### Structure

```
src/
├── main.tsx                  React entry point, QueryClientProvider
├── App.tsx                   Router setup — 3 routes
├── types/posting.ts          TypeScript interfaces matching backend DTOs
├── api/postingApi.ts         All axios calls — unwraps ApiResponse<T>
├── pages/
│   ├── PostingListPage.tsx   Search form + paginated table + Retry button
│   ├── PostingDetailPage.tsx Posting fields + leg table
│   └── CreatePostingPage.tsx New posting form
└── components/
    ├── StatusBadge.tsx       Coloured badge for PENDING / SUCCESS / FAILED
    └── LegTable.tsx          Renders a LegResponse[]
```

Vite proxies `/api` → `http://localhost:8080` in dev — no CORS config needed.
