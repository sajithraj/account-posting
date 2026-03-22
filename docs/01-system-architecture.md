# System Architecture

This document presents the Account Posting Orchestrator at two levels of abstraction using the C4 model.

---

## C4 Level 1 — Context Diagram

Shows the system as a single black box and its relationships with external actors and systems.

```mermaid
C4Context
    title Account Posting Orchestrator — System Context

    Person(ops, "Operations Team", "Monitors postings, triggers manual retries via the web UI")
    Person(upstream, "Upstream Caller", "Internal services (payment hub, teller, batch) that submit posting requests via REST")

    System(aps, "Account Posting Orchestrator", "Accepts posting requests, routes to core banking systems, tracks every leg, provides retry UI")

    System_Ext(cbs, "Core Banking System (CBS)", "Processes debit/credit instructions — primary ledger")
    System_Ext(gl, "General Ledger (GL)", "Accounting entry system for GL postings")
    System_Ext(obpm, "OBPM", "Oracle Banking Payments Manager — handles payment instructions")
    System_Ext(kafka, "Kafka", "Event bus — receives PostingSuccessEvent when a posting fully succeeds")
    System_Ext(downstream, "Downstream Consumers", "Notification service, audit, reconciliation — consume PostingSuccessEvent")

    Rel(upstream, aps, "POST /account-posting", "HTTPS/JSON")
    Rel(ops, aps, "Search, view, retry postings", "HTTPS/Browser")
    Rel(aps, cbs, "Submit posting legs", "HTTP (stub)")
    Rel(aps, gl, "Submit posting legs", "HTTP (stub)")
    Rel(aps, obpm, "Submit posting legs", "HTTP (stub)")
    Rel(aps, kafka, "Publish PostingSuccessEvent", "Kafka Producer (optional)")
    Rel(kafka, downstream, "Consume PostingSuccessEvent", "Kafka Consumer")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

---

## C4 Level 2 — Container Diagram

Zooms into the Account Posting Orchestrator boundary showing all deployable units and their interactions.

```mermaid
C4Container
    title Account Posting Orchestrator — Container View

    Person(ops, "Operations Team", "")
    Person(upstream, "Upstream Caller", "")

    System_Boundary(aps_boundary, "Account Posting Orchestrator") {
        Container(ui, "Web UI", "React 18, TypeScript, Vite", "Provides search, detail view, and retry trigger. Runs on port 3000. Proxies /api to Spring Boot.")
        Container(api, "REST API", "Spring Boot 3.2, Java 21", "Handles posting lifecycle: create, retry, search. Executes strategies for CBS/GL/OBPM. Runs on port 8080.")
        ContainerDb(db, "PostgreSQL Database", "PostgreSQL 15+", "Stores account_posting, account_posting_leg, posting_config tables.")
    }

    System_Ext(cbs, "CBS", "Core Banking System")
    System_Ext(gl, "GL", "General Ledger")
    System_Ext(obpm, "OBPM", "Oracle Banking Payments Manager")
    System_Ext(kafka, "Kafka Cluster", "Event streaming — optional")

    Rel(ops, ui, "Uses", "HTTPS/Browser :3000")
    Rel(upstream, api, "POST /account-posting, GET /account-posting", "HTTPS :8080")
    Rel(ui, api, "API calls via /api proxy", "HTTP (Vite dev proxy)")
    Rel(api, db, "Read/Write entities", "JDBC / Spring Data JPA")
    Rel(api, cbs, "Submit leg (stub)", "HTTP")
    Rel(api, gl, "Submit leg (stub)", "HTTP")
    Rel(api, obpm, "Submit leg (stub)", "HTTP")
    Rel(api, kafka, "Publish PostingSuccessEvent", "Kafka (conditional)")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

---

## Notes

- **Vite proxy**: In development, the Vite dev server proxies all `/api` requests to `http://localhost:8080`, eliminating CORS issues.
- **Kafka conditional**: The `PostingEventPublisher` bean is only registered when `kafka.enabled=true`. When disabled, no Kafka dependency is required at runtime.
- **External system stubs**: CBS, GL, and OBPM clients are currently stubs (`CoreBankingClient` / strategy impls). Replace with real HTTP clients for production.
- **Single JVM**: The `posting` and `leg` packages both run inside the Spring Boot JVM and communicate via direct Java method calls — no inter-service HTTP.
