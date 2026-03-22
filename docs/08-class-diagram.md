# Class Diagram

UML class diagram for the core domain model including the Strategy pattern, JPA entities, DTOs, and MapStruct mappers.

---

## Strategy Pattern

```mermaid
classDiagram
    class PostingStrategy {
        <<interface>>
        +getPostingFlow() String
        +process(AccountPostingRequest request, Long existingLegId, boolean isRetry) LegResponse
    }

    class PostingStrategyFactory {
        -Map~String, PostingStrategy~ strategyMap
        +PostingStrategyFactory(List~PostingStrategy~ strategies)
        +getStrategy(String postingFlow) PostingStrategy
    }

    class CBSPostingService {
        -AccountPostingLegService legService
        +getPostingFlow() String
        +process(AccountPostingRequest, Long, boolean) LegResponse
        -buildExternalRequest(AccountPostingRequest, AccountPostingLeg) Object
        -callExternalSystem(Object) ExternalCallResult
    }

    class GLPostingService {
        -AccountPostingLegService legService
        +getPostingFlow() String
        +process(AccountPostingRequest, Long, boolean) LegResponse
        -buildExternalRequest(AccountPostingRequest, AccountPostingLeg) Object
        -callExternalSystem(Object) ExternalCallResult
    }

    class OBPMPostingService {
        -AccountPostingLegService legService
        +getPostingFlow() String
        +process(AccountPostingRequest, Long, boolean) LegResponse
        -buildExternalRequest(AccountPostingRequest, AccountPostingLeg) Object
        -callExternalSystem(Object) ExternalCallResult
    }

    PostingStrategy <|.. CBSPostingService : implements
    PostingStrategy <|.. GLPostingService : implements
    PostingStrategy <|.. OBPMPostingService : implements
    PostingStrategyFactory o-- PostingStrategy : indexes by getPostingFlow()

    note for PostingStrategyFactory "getPostingFlow() values:\nCBS_POSTING\nGL_POSTING\nOBPM_POSTING"
```

---

## Entities and Enums

```mermaid
classDiagram
    class BaseEntity {
        <<abstract>>
        #LocalDateTime createdAt
        #LocalDateTime updatedAt
    }

    class AccountPosting {
        -Long postingId
        -String sourceReferenceId
        -String endToEndReferenceId
        -String sourceName
        -String requestType
        -BigDecimal amount
        -String currency
        -CreditDebitIndicator creditDebitIndicator
        -String debtorAccount
        -String creditorAccount
        -LocalDate requestedExecutionDate
        -String remittanceInformation
        -PostingStatus status
        -String targetSystems
        -String requestPayload
        -String responsePayload
        -OffsetDateTime retryLockedUntil
    }

    class PostingStatus {
        <<enumeration>>
        PENDING
        SUCCESS
        FAILED
    }

    class CreditDebitIndicator {
        <<enumeration>>
        CREDIT
        DEBIT
    }

    class AccountPostingLeg {
        -Long postingLegId
        -Long postingId
        -Integer legOrder
        -String legType
        -String targetSystem
        -String account
        -LegStatus status
        -String referenceId
        -String reason
        -Integer attemptNumber
        -OffsetDateTime postedTime
        -String requestPayload
        -String responsePayload
        -LegMode mode
        -String operation
        +Integer version
    }

    class LegStatus {
        <<enumeration>>
        PENDING
        SUCCESS
        FAILED
    }

    class LegMode {
        <<enumeration>>
        NORM
        RETRY
        MANUAL
    }

    class PostingConfig {
        -Long configId
        -String sourceName
        -String requestType
        -String targetSystem
        -String operation
        -Integer orderSeq
    }

    BaseEntity <|-- AccountPosting : extends
    BaseEntity <|-- AccountPostingLeg : extends
    BaseEntity <|-- PostingConfig : extends

    AccountPosting --> PostingStatus : status
    AccountPosting --> CreditDebitIndicator : creditDebitIndicator
    AccountPostingLeg --> LegStatus : status
    AccountPostingLeg --> LegMode : mode

    AccountPosting "1" --> "0..*" AccountPostingLeg : postingId (plain Long, no JPA join)

    note for AccountPostingLeg "@Version on version field\nprovides JPA optimistic locking\non top of retry_locked_until timestamp"
    note for AccountPosting "requestPayload and responsePayload\nstored as JSONB in PostgreSQL\nserialized by ObjectMapper"
```

---

## DTO Classes

```mermaid
classDiagram
    class AccountPostingRequest {
        +String sourceReferenceId
        +String endToEndReferenceId
        +String sourceName
        +String requestType
        +BigDecimal amount
        +String currency
        +String creditDebitIndicator
        +String debtorAccount
        +String creditorAccount
        +LocalDate requestedExecutionDate
        +String remittanceInformation
    }

    class AccountPostingResponse {
        +Long postingId
        +String sourceReferenceId
        +String endToEndReferenceId
        +String sourceName
        +String requestType
        +BigDecimal amount
        +String currency
        +String creditDebitIndicator
        +String debtorAccount
        +String creditorAccount
        +LocalDate requestedExecutionDate
        +String remittanceInformation
        +String status
        +String targetSystems
        +List~LegResponse~ legs
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }

    class LegResponse {
        +Long postingLegId
        +Long postingId
        +Integer legOrder
        +String legType
        +String targetSystem
        +String account
        +String status
        +String referenceId
        +String reason
        +Integer attemptNumber
        +OffsetDateTime postedTime
        +String mode
        +String operation
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }

    class RetryRequest {
        +List~Long~ postingIds
    }

    class RetryResponse {
        +int attempted
        +int succeeded
        +int stillPending
        +int skipped
        +List~RetryResult~ results
    }

    class AccountPostingSearchRequest {
        +String status
        +String requestType
        +String sourceName
        +String sourceReferenceId
        +String endToEndReferenceId
        +LocalDateTime dateFrom
        +LocalDateTime dateTo
        +BigDecimal amountMin
        +BigDecimal amountMax
        +int page
        +int size
        +String sort
    }

    class ExternalCallResult {
        +boolean success
        +String referenceId
        +String reason
        +Object rawResponse
    }

    class AccountPostingLegRequest {
        +Long postingId
        +Integer legOrder
        +String legType
        +String targetSystem
        +String account
        +String mode
        +String operation
    }

    class UpdateLegRequest {
        +Long legId
        +LegStatus status
        +String referenceId
        +String reason
        +OffsetDateTime postedTime
        +String requestPayload
        +String responsePayload
        +LegMode mode
        +Integer attemptNumber
    }

    AccountPostingResponse *-- LegResponse : legs
    RetryResponse *-- ExternalCallResult : results
```

---

## MapStruct Mappers

```mermaid
classDiagram
    class AccountPostingMapper {
        <<interface>>
        <<MapStruct @Mapper>>
        +toEntity(AccountPostingRequest) AccountPosting
        +toResponse(AccountPosting, List~LegResponse~) AccountPostingResponse
        +toLegResponse(AccountPostingLeg) LegResponse
    }

    class AccountPostingLegMapper {
        <<interface>>
        <<MapStruct @Mapper>>
        +toEntity(AccountPostingLegRequest) AccountPostingLeg
        +toResponse(AccountPostingLeg) AccountPostingLegResponse
        +toUpdateEntity(UpdateLegRequest, AccountPostingLeg) AccountPostingLeg
    }

    AccountPostingMapper ..> AccountPosting : reads/writes
    AccountPostingMapper ..> AccountPostingRequest : reads
    AccountPostingMapper ..> AccountPostingResponse : writes
    AccountPostingMapper ..> LegResponse : writes

    AccountPostingLegMapper ..> AccountPostingLeg : reads/writes
    AccountPostingLegMapper ..> AccountPostingLegRequest : reads
    AccountPostingLegMapper ..> UpdateLegRequest : reads
```

---

## Key Notes

| Design Decision | Rationale |
|-----------------|-----------|
| **`PostingStrategy` interface** | Enables the Factory to hold all implementations in a `Map` without any `instanceof` or switch logic. Adding a new external system requires only a new `@Service` implementing the interface. |
| **`getPostingFlow()` as map key** | Each strategy self-declares its key. `PostingStrategyFactory` collects them at application startup via `List<PostingStrategy>` injection. |
| **`AccountPostingLeg.postingId` as `Long`** | Not a JPA `@ManyToOne` — prevents the `leg` package from importing `AccountPosting`. Package boundary enforced at the JVM level. |
| **`@Version` on `AccountPostingLeg`** | Provides optimistic locking at the JPA layer. Combined with the `retry_locked_until` timestamp on the posting, gives two layers of concurrency protection. |
| **JSONB payloads on entities** | `requestPayload` and `responsePayload` are stored as plain `String` (JSONB in DB). Deserialization via `ObjectMapper` happens explicitly in `PostingRetryProcessor` when reconstructing the request for retry. |
| **MapStruct over manual mapping** | Compile-time generated mappers eliminate runtime reflection. Explicit `@Mapping` annotations make field-level transformations traceable. |
