# Account Posting Orchestrator — Architecture & Design Diagrams

This directory contains the complete set of architecture and design diagrams for the Account Posting Orchestrator
system. All diagrams are written in [Mermaid](https://mermaid.js.org/) and render natively in GitHub, GitLab, and most
modern documentation tools.

## System Overview

The Account Posting Orchestrator accepts posting requests from upstream systems, routes them to one or more core banking
systems (CBS, GL, OBPM) according to a configurable strategy, tracks every response leg, and provides a React UI with
search and retry capabilities.

| Component | Technology                      | Port |
|-----------|---------------------------------|------|
| REST API  | Spring Boot 3.5, Java 21, Maven | 8080 |
| Web UI    | React 18, TypeScript, Vite      | 3000 |
| Database  | PostgreSQL                      | 5432 |
| Messaging | Kafka (optional)                | 9092 |

---

## Diagram Index

| #  | File                                                             | What it shows                                                           |
|----|------------------------------------------------------------------|-------------------------------------------------------------------------|
| 1  | [01-system-architecture.md](./01-system-architecture.md)         | C4 Context and C4 Container views — the system in its environment       |
| 2  | [02-database-design.md](./02-database-design.md)                 | Full ERD — all tables, columns, and relationships                       |
| 3  | [03-sequence-create-posting.md](./03-sequence-create-posting.md) | CREATE flow — end-to-end sequence from HTTP request to Kafka event      |
| 4  | [04-sequence-retry.md](./04-sequence-retry.md)                   | RETRY flow — parallel CompletableFuture processing with lock mechanism  |
| 5  | [05-sequence-search.md](./05-sequence-search.md)                 | SEARCH flow — dynamic JPA Specification predicate building              |
| 6  | [06-state-machine.md](./06-state-machine.md)                     | State machines — Posting and Leg status transitions with triggers       |
| 7  | [07-component-diagram.md](./07-component-diagram.md)             | Spring Boot component diagram — internal wiring of all beans            |
| 8  | [08-class-diagram.md](./08-class-diagram.md)                     | Class diagram — Strategy pattern, entities, DTOs, mappers               |
| 9  | [09-deployment-diagram.md](./09-deployment-diagram.md)           | Production deployment — Docker, Nginx, replicas, Kafka, monitoring      |
| 10 | [10-data-flow-diagram.md](./10-data-flow-diagram.md)             | Data flow — request transformation, mapping layers, payload persistence |
| 11 | [11-error-handling-flow.md](./11-error-handling-flow.md)         | Error handling — exception hierarchy, retry paths, duplicate guard      |

---

## Key Design Decisions (Quick Reference)

1. **Leg decoupling** — `AccountPostingLeg.postingId` is a plain `Long` column (not `@ManyToOne`). The `leg` package
   never imports from `posting`.
2. **Pre-insert legs** — All legs are inserted as `PENDING` before any external call, so retry can always find every leg
   even if the first call fails mid-flight.
3. **Atomic retry lock** — A single `@Modifying` UPDATE sets `retry_locked_until = NOW() + 2 min` on the posting row. No
   row-level DB lock needed.
4. **Parallel retry** — One `CompletableFuture` per locked posting dispatched to a dedicated `retryExecutor` thread
   pool (core=4, max=10, CallerRunsPolicy on overflow).
5. **Strategy pattern** — `PostingStrategyFactory` maps `"CBS_POSTING"`, `"GL_POSTING"`, `"OBPM_POSTING"` to concrete
   implementations resolved from `posting_config`.
6. **ExternalApiHelper** — All outbound payload builders and stub call methods for CBS, GL, and OBPM are centralised in
   one `@Component`. Replace stub `call*()` methods with real HTTP clients before go-live.
7. **Kafka optional** — `@ConditionalOnProperty(kafka.enabled=true)` + null guard; the publisher bean is absent when
   Kafka is off.
8. **No HTTP between packages** — Both `posting` and `leg` packages run in the same JVM and communicate via direct Java
   method calls.
9. **Snake_case API** — Jackson `SNAKE_CASE` naming strategy configured globally. All JSON bodies in and out are
   snake_case.
10. **Env-specific DB migrations** — Each environment (`dev`, `qa`, `uat`, `prod`, `docker`) has its own Flyway
    migration folder. Scripts are explicitly reviewed and copied on promotion — nothing applies automatically across
    environments.
