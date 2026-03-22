# Deployment Diagram

Production deployment topology for the Account Posting Orchestrator. Shows Docker containers, network boundaries,
redundancy, and observability infrastructure.

---

## Production Deployment Architecture

```mermaid
graph TB
    subgraph Internet["Internet / Internal Network"]
        UpstreamCallers["Upstream Callers\n(Payment Hub, Teller, Batch)"]
        OpsTeam["Operations Team\n(Browser)"]
    end

    subgraph DMZ["DMZ / Reverse Proxy Layer"]
        Nginx["Nginx\nReverse Proxy / Load Balancer\n:443 HTTPS\n:80 → redirect to 443\n\nRoutes:\n/api/* → api-container:8080\n/* → ui-container:3000"]
    end

    subgraph AppNetwork["Application Network (Docker bridge: app-net)"]
        subgraph UIContainers["UI Tier"]
            UI["ui-container\nNginx static server\nReact 18 built assets\n:3000"]
        end

        subgraph APIContainers["API Tier (horizontally scalable)"]
            API1["api-container-1\nSpring Boot 3.2\nJava 21 JRE\n:8080\n\nJVM: -Xms512m -Xmx1g\nProfile: prod"]
            API2["api-container-2\nSpring Boot 3.2\nJava 21 JRE\n:8080\n\n(scale as needed)"]
        end

        subgraph DataTier["Data Tier"]
            PGPrimary["postgres-primary\nPostgreSQL 15\n:5432\nRW connections\nVolume: pg-data-primary"]
            PGReplica["postgres-replica\nPostgreSQL 15\n:5432\nRO connections\nStreaming replication\nVolume: pg-data-replica"]
        end

        subgraph MessageTier["Messaging Tier (optional)"]
            Kafka1["kafka-broker-1\nKafka 3.x\n:9092\nTopic: posting.success\npartitions=6, replication=2"]
            Kafka2["kafka-broker-2\nKafka 3.x\n:9092"]
            Zookeeper["zookeeper\n:2181\n(or KRaft mode)"]
        end
    end

    subgraph ExternalStubs["External Systems (replace stubs in prod)"]
        CBS["CBS\nCore Banking System\nHTTP API"]
        GL["GL\nGeneral Ledger\nHTTP API"]
        OBPM["OBPM\nOracle Banking Payments\nHTTP API"]
    end

    subgraph ObservabilityStack["Observability Network (obs-net)"]
        Prometheus["Prometheus\n:9090\nScrapes /actuator/metrics"]
        Grafana["Grafana\n:3000 (internal)\nDashboards: JVM, DB, Kafka"]
        Loki["Loki\nLog aggregation\n:3100"]
        Promtail["Promtail\nLog shipper\n(runs as sidecar or DaemonSet)"]
        AlertManager["AlertManager\n:9093\nRoutes alerts → PagerDuty / Slack"]
    end

    UpstreamCallers -->|"HTTPS :443"| Nginx
    OpsTeam -->|"HTTPS :443"| Nginx

    Nginx -->|"/api/* HTTP"| API1
    Nginx -->|"/api/* HTTP"| API2
    Nginx -->|"/* HTTP"| UI

    API1 -->|"JDBC :5432"| PGPrimary
    API2 -->|"JDBC :5432"| PGPrimary
    PGPrimary -->|"Streaming replication"| PGReplica

    API1 -->|"Kafka Producer\n(if kafka.enabled=true)"| Kafka1
    API2 -->|"Kafka Producer"| Kafka1
    Kafka1 <-->|"Broker sync"| Kafka2
    Kafka1 --> Zookeeper
    Kafka2 --> Zookeeper

    API1 -->|"HTTP stub"| CBS
    API1 -->|"HTTP stub"| GL
    API1 -->|"HTTP stub"| OBPM
    API2 -->|"HTTP stub"| CBS
    API2 -->|"HTTP stub"| GL
    API2 -->|"HTTP stub"| OBPM

    Prometheus -->|"Scrape /actuator/prometheus"| API1
    Prometheus -->|"Scrape /actuator/prometheus"| API2
    Prometheus --> Grafana
    Prometheus --> AlertManager
    Promtail -->|"Ship logs"| Loki
    Loki --> Grafana

    style Internet fill:#fce4ec,stroke:#e91e63
    style DMZ fill:#e3f2fd,stroke:#2196f3
    style AppNetwork fill:#f3e5f5,stroke:#9c27b0
    style ExternalStubs fill:#fff3e0,stroke:#ff9800
    style ObservabilityStack fill:#e8f5e9,stroke:#4caf50
```

---

## Docker Compose Service Definitions

```mermaid
block-beta
    columns 3

    block:compose["docker-compose.yml services"]:3
        ui["ui\nimage: account-posting-ui\nbuild: ./ui\nports: 3000:80\ndepends_on: -"]

        api["api\nimage: account-posting-api\nbuild: ./account-posting\nports: 8080:8080\nenv: SPRING_PROFILES_ACTIVE=prod\nenv: DB_URL, DB_USER, DB_PASS\nenv: KAFKA_BOOTSTRAP (optional)\ndepends_on: postgres"]

        postgres["postgres\nimage: postgres:15-alpine\nports: 5432:5432 (internal)\nvolumes: pg-data:/var/lib/postgresql/data\nenv: POSTGRES_DB=account_posting_db"]

        kafka["kafka\nimage: bitnami/kafka:latest\nports: 9092:9092 (internal)\ndepends_on: zookeeper\nenv: KAFKA_BROKER_ID=1"]

        zookeeper["zookeeper\nimage: bitnami/zookeeper\nports: 2181:2181 (internal)"]

        flyway["flyway-migrate\nimage: flyway/flyway\ncommand: migrate\nvolumes: ./db/migrations\ndepends_on: postgres\n(runs once, exits)"]
    end
```

---

## Network and Port Reference

| Service            | Internal Port | Exposed          | Protocol   | Notes                                      |
|--------------------|---------------|------------------|------------|--------------------------------------------|
| Nginx              | 443, 80       | 443, 80          | HTTPS/HTTP | Entry point; redirects 80→443              |
| React UI           | 3000          | via Nginx        | HTTP       | Static files served by Nginx in production |
| Spring Boot API    | 8080          | via Nginx `/api` | HTTP       | Multiple instances behind Nginx upstream   |
| PostgreSQL primary | 5432          | NOT exposed      | TCP/JDBC   | Internal only                              |
| PostgreSQL replica | 5432          | NOT exposed      | TCP/JDBC   | Read-only; optional in dev                 |
| Kafka broker(s)    | 9092          | NOT exposed      | TCP        | Internal only                              |
| Prometheus         | 9090          | Internal         | HTTP       | Ops network only                           |
| Grafana            | 3000          | Internal :3001   | HTTP       | Ops network only                           |
| Loki               | 3100          | Internal         | HTTP       | Ops network only                           |

---

## Key Notes

| Aspect                     | Detail                                                                                                                                                                                           |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Horizontal API scaling** | Multiple `api-container` instances behind Nginx. Retry lock in PostgreSQL (`retry_locked_until`) ensures no two instances process the same posting simultaneously.                               |
| **Database migrations**    | Run the `flyway-migrate` one-shot container (or the `db/` Maven module) before starting the API. The Spring Boot app has no embedded Flyway — it expects a migrated schema.                      |
| **Kafka optional**         | Set `KAFKA_ENABLED=false` (maps to `kafka.enabled=false`) to run without Kafka. The `PostingEventPublisher` bean is simply absent.                                                               |
| **Secrets management**     | In production, inject `DB_URL`, `DB_USER`, `DB_PASS`, `KAFKA_BOOTSTRAP_SERVERS` via environment variables or a secrets manager (Vault, AWS Secrets Manager). Never bake credentials into images. |
| **JVM tuning**             | `-Xms512m -Xmx1g` recommended per instance. Adjust based on thread pool size (`retryExecutor`) and connection pool size (HikariCP default 10).                                                   |
| **Health checks**          | Spring Actuator exposes `/actuator/health` for Docker/K8s liveness and readiness probes. Prometheus scrapes `/actuator/prometheus`.                                                              |
| **Log shipping**           | Promtail (Grafana agent) tails container logs and ships to Loki. Structured JSON logging recommended — MDC fields (`traceId`, `postingId`) become queryable labels in Loki.                      |
