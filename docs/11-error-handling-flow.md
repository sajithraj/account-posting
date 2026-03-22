# Error Handling Flow

Shows how exceptions are caught and translated, what happens when external systems fail, how duplicate submissions are
handled, and how the retry lock expiry path works.

---

## Exception Hierarchy and HTTP Mapping

```mermaid
classDiagram
    class RuntimeException {
        <<Java>>
    }

    class BusinessException {
        -String errorCode
        -String message
        +BusinessException(String errorCode, String message)
        note "→ HTTP 422 Unprocessable Entity"
    }

    class ResourceNotFoundException {
        -String resourceName
        -Long resourceId
        +ResourceNotFoundException(String resource, Long id)
        note "→ HTTP 404 Not Found"
    }

    class MethodArgumentNotValidException {
        <<Spring MVC>>
        note "→ HTTP 400 Bad Request\n(Jakarta Bean Validation failures)"
    }

    class HttpMessageNotReadableException {
        <<Spring MVC>>
        note "→ HTTP 400 Bad Request\n(Malformed JSON body)"
    }

    class Exception {
        <<Java>>
        note "→ HTTP 500 Internal Server Error\n(all unhandled exceptions)"
    }

    class GlobalExceptionHandler {
        <<@RestControllerAdvice>>
        +handleBusinessException(BusinessException) ApiResponse
        +handleResourceNotFound(ResourceNotFoundException) ApiResponse
        +handleValidation(MethodArgumentNotValidException) ApiResponse
        +handleBadRequest(HttpMessageNotReadableException) ApiResponse
        +handleGeneric(Exception) ApiResponse
    }

    RuntimeException <|-- BusinessException
    RuntimeException <|-- ResourceNotFoundException
    GlobalExceptionHandler ..> BusinessException : @ExceptionHandler
    GlobalExceptionHandler ..> ResourceNotFoundException : @ExceptionHandler
    GlobalExceptionHandler ..> MethodArgumentNotValidException : @ExceptionHandler
    GlobalExceptionHandler ..> HttpMessageNotReadableException : @ExceptionHandler
    GlobalExceptionHandler ..> Exception : @ExceptionHandler
```

---

## GlobalExceptionHandler Response Mapping

```mermaid
flowchart TD
    EX["Exception thrown\n(any layer)"]

    EX --> IS_BIZ{BusinessException?}
    IS_BIZ -- Yes --> BIZ422["HTTP 422\nApiResponse {\n  success: false,\n  error: {\n    code: ex.errorCode,\n    message: ex.message\n  }\n}"]

    IS_BIZ -- No --> IS_404{ResourceNotFoundException?}
    IS_404 -- Yes --> RNF404["HTTP 404\nApiResponse {\n  success: false,\n  error: {\n    code: 'NOT_FOUND',\n    message: 'AccountPosting 99 not found'\n  }\n}"]

    IS_404 -- No --> IS_VALID{MethodArgumentNotValidException\nor HttpMessageNotReadable?}
    IS_VALID -- Yes --> VAL400["HTTP 400\nApiResponse {\n  success: false,\n  error: {\n    code: 'VALIDATION_ERROR',\n    message: '...',\n    fieldErrors: [\n      { field: 'amount', message: 'must be positive' }\n    ]\n  }\n}"]

    IS_VALID -- No --> GENERIC["HTTP 500\nApiResponse {\n  success: false,\n  error: {\n    code: 'INTERNAL_ERROR',\n    message: 'An unexpected error occurred'\n  }\n}\nStack trace logged at ERROR level\nwith MDC fields"]

    style BIZ422 fill:#fff3cd,stroke:#ff9800
    style RNF404 fill:#f9d4d4,stroke:#f44336
    style VAL400 fill:#cce5ff,stroke:#007bff
    style GENERIC fill:#f9d4d4,stroke:#f44336
```

---

## Duplicate endToEndReferenceId Handling

```mermaid
sequenceDiagram
    participant Client
    participant Ctrl as AccountPostingController
    participant Svc as AccountPostingService
    participant Repo as AccountPostingRepository
    participant GEH as GlobalExceptionHandler

    Client->>Ctrl: POST /account-posting<br/>{ endToEndReferenceId: "E2E-001", ... }
    Ctrl->>Svc: createPosting(request)
    Svc->>Repo: existsByEndToEndReferenceId("E2E-001")
    Repo-->>Svc: true (already exists)

    Svc->>Svc: throw BusinessException(\n  "DUPLICATE_REFERENCE",\n  "Posting with endToEndReferenceId E2E-001 already exists"\n)

    Svc-->>GEH: BusinessException propagates

    GEH->>GEH: @ExceptionHandler(BusinessException.class)
    GEH-->>Ctrl: ResponseEntity(422, ApiResponse { success:false, errorCode:"DUPLICATE_REFERENCE" })
    Ctrl-->>Client: 422 Unprocessable Entity

    Note over Repo: UNIQUE constraint on end_to_end_reference_id\nacts as second line of defence\n(catches race conditions between the\nexistsBy check and the INSERT)

    Note over GEH: DataIntegrityViolationException\nfrom the UNIQUE violation is also\ncaught by generic handler → 500,\nbut the explicit check catches it first → 422
```

---

## External System Failure Path

```mermaid
flowchart TD
    subgraph Create_Flow["During Create Flow (Loop 2)"]
        CALL["Strategy calls External System\nCBSPostingService.callExternalSystem()"]
        CALL --> EXT_FAIL{External system\nresponse?}

        EXT_FAIL -- "SUCCESS\nExternalCallResult.success=true" --> LEG_SUCCESS["leg.status = SUCCESS\nleg.referenceId = CBS-REF-001\nleg.postedTime = NOW()"]

        EXT_FAIL -- "FAILED\nExternalCallResult.success=false" --> LEG_FAILED["leg.status = FAILED\nleg.reason = 'Timeout / error message'\nleg.responsePayload = raw error"]

        EXT_FAIL -- "Exception thrown\n(unchecked)" --> CATCH["Strategy catches exception\nLogs error with MDC\nReturns ExternalCallResult { success=false }"]
        CATCH --> LEG_FAILED

        LEG_SUCCESS --> NEXT_LEG["Proceed to next leg\n(Loop 2 continues regardless)"]
        LEG_FAILED --> NEXT_LEG

        NEXT_LEG --> ALL_DONE["All legs processed"]
        ALL_DONE --> EVAL{All legs SUCCESS?}

        EVAL -- "Yes" --> POST_SUCCESS["AccountPosting.status = SUCCESS\nPublish PostingSuccessEvent"]
        EVAL -- "No (any FAILED or PENDING)" --> POST_PENDING["AccountPosting.status = PENDING\n(eligible for retry)"]
    end

    subgraph Retry_Flow["During Retry Flow"]
        RETRY_CALL["PostingRetryProcessor\nStrategy calls External System again"]
        RETRY_CALL --> RETRY_RESULT{Result?}

        RETRY_RESULT -- "SUCCESS" --> RETRY_LEG_SUCCESS["leg.status = SUCCESS\nleg.mode = RETRY\nleg.attemptNumber++\nleg.postedTime = NOW()"]

        RETRY_RESULT -- "FAILED or Exception" --> RETRY_LEG_FAILED["leg.status = FAILED\nleg.mode = RETRY\nleg.attemptNumber++\nleg.reason updated"]

        RETRY_LEG_SUCCESS --> RETRY_EVAL{All legs SUCCESS?}
        RETRY_LEG_FAILED --> RETRY_EVAL

        RETRY_EVAL -- "Yes" --> RETRY_POST_SUCCESS["AccountPosting.status = SUCCESS\nPublish PostingSuccessEvent\nretry_locked_until expires naturally"]
        RETRY_EVAL -- "No" --> RETRY_POST_PENDING["AccountPosting.status = PENDING\nretry_locked_until expires in ~2min\nPosting available for next retry"]
    end

    style LEG_SUCCESS fill:#d4f9d4,stroke:#4caf50
    style LEG_FAILED fill:#f9d4d4,stroke:#f44336
    style POST_SUCCESS fill:#d4f9d4,stroke:#4caf50
    style POST_PENDING fill:#fff3cd,stroke:#ff9800
    style RETRY_LEG_SUCCESS fill:#d4f9d4,stroke:#4caf50
    style RETRY_LEG_FAILED fill:#f9d4d4,stroke:#f44336
    style RETRY_POST_SUCCESS fill:#d4f9d4,stroke:#4caf50
    style RETRY_POST_PENDING fill:#fff3cd,stroke:#ff9800
```

---

## Retry Lock Expiry and Race Condition Prevention

```mermaid
flowchart TD
    subgraph Retry_Lock_Flow["Retry Lock Mechanism"]
        R1["POST /retry received\n(Instance A or B)"]
        R1 --> R2["Atomic UPDATE:\nSET retry_locked_until = NOW() + 2min\nWHERE (retry_locked_until IS NULL\n  OR retry_locked_until < NOW())"]

        R2 --> R3{Rows updated?}
        R3 -- "0 rows — posting\nalready locked" --> SKIP["Posting skipped\ncounted as 'skipped' in response\nNo duplicate processing"]
        R3 -- "N rows locked" --> PROCESS["Dispatch CompletableFutures\nfor locked postings"]

        PROCESS --> PROC_END{Processing complete?}
        PROC_END -- "All legs SUCCESS" --> CLEAR_LOCK["Posting.status = SUCCESS\nretry_locked_until NOT cleared\n(expires naturally in < 2min)"]
        PROC_END -- "Some legs still PENDING/FAILED" --> KEEP_PENDING["Posting.status = PENDING\nretry_locked_until expires in < 2min\nAvailable for next retry cycle"]
    end

    subgraph Race_Condition["Race Condition: Two simultaneous retry calls"]
        RC1["Retry Call 1 (Instance A)\nlockForRetry(postingId=42)"]
        RC2["Retry Call 2 (Instance B)\nlockForRetry(postingId=42)"]

        RC1 -->|"UPDATE acquires lock\n(updates 1 row)"| RC1_WIN["Instance A processes posting 42"]
        RC2 -->|"UPDATE finds retry_locked_until > NOW()\n(updates 0 rows)"| RC2_SKIP["Instance B skips posting 42"]
    end

    style SKIP fill:#f9d4d4,stroke:#f44336
    style PROCESS fill:#d4f9d4,stroke:#4caf50
    style RC1_WIN fill:#d4f9d4,stroke:#4caf50
    style RC2_SKIP fill:#fff3cd,stroke:#ff9800
```

---

## Validation Error Flow

```mermaid
sequenceDiagram
    participant Client
    participant Ctrl as AccountPostingController
    participant GEH as GlobalExceptionHandler

    Client->>Ctrl: POST /account-posting\n{ amount: -100, currency: "", ... }

    Note over Ctrl: @Valid on request body\nJakarta Bean Validation triggers

    Ctrl->>Ctrl: @NotBlank(currency) fails\n@Positive(amount) fails\n@NotNull(debtorAccount) fails

    Ctrl-->>GEH: MethodArgumentNotValidException
    GEH->>GEH: Extract BindingResult.getFieldErrors()
    GEH-->>Client: 400 Bad Request\n{\n  success: false,\n  error: {\n    code: "VALIDATION_ERROR",\n    message: "Validation failed",\n    fieldErrors: [\n      { field: "amount", message: "must be greater than 0" },\n      { field: "currency", message: "must not be blank" },\n      { field: "debtorAccount", message: "must not be null" }\n    ]\n  }\n}
```

---

## Error Handling Summary Table

| Scenario                         | Exception                                                       | HTTP Status                   | Error Code            | Leg Effect       | Posting Effect                |
|----------------------------------|-----------------------------------------------------------------|-------------------------------|-----------------------|------------------|-------------------------------|
| Duplicate `endToEndReferenceId`  | `BusinessException`                                             | 422                           | `DUPLICATE_REFERENCE` | None created     | None created                  |
| Request field validation failure | `MethodArgumentNotValidException`                               | 400                           | `VALIDATION_ERROR`    | None created     | None created                  |
| Malformed JSON body              | `HttpMessageNotReadableException`                               | 400                           | `BAD_REQUEST`         | None created     | None created                  |
| Posting not found by ID          | `ResourceNotFoundException`                                     | 404                           | `NOT_FOUND`           | No change        | No change                     |
| External system returns failure  | Caught in strategy, returns `ExternalCallResult{success=false}` | 200 (normal flow continues)   | N/A                   | `status=FAILED`  | `status=PENDING`              |
| External system throws exception | Caught in strategy, logged, treated as failure                  | 200 (normal flow continues)   | N/A                   | `status=FAILED`  | `status=PENDING`              |
| All legs succeed                 | No exception                                                    | 200                           | N/A                   | `status=SUCCESS` | `status=SUCCESS`              |
| Retry — posting already locked   | No exception, skipped                                           | 200                           | N/A                   | No change        | No change                     |
| Kafka publish failure            | Logged at ERROR, not rethrown                                   | 200 (posting already SUCCESS) | N/A                   | No change        | Remains SUCCESS               |
| Unhandled runtime exception      | `Exception`                                                     | 500                           | `INTERNAL_ERROR`      | Varies           | Varies (transaction rollback) |

---

## Key Notes

| Design Decision                          | Rationale                                                                                                                                                                                                                                                                                            |
|------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Strategy catches external exceptions** | External system failures must not propagate up and roll back the entire transaction. The strategy catches all exceptions, logs them, and returns a `FAILED` result so the posting stays `PENDING` for retry.                                                                                         |
| **Loop 2 continues after failure**       | All legs are attempted regardless of whether a previous leg failed. This ensures the maximum number of legs are processed in each invocation.                                                                                                                                                        |
| **UNIQUE DB constraint as backup**       | Even if the `existsByEndToEndReferenceId` check passes in a race condition (two simultaneous requests), the DB UNIQUE constraint on `end_to_end_reference_id` guarantees only one INSERT succeeds. The resulting `DataIntegrityViolationException` should be caught and mapped to 422 in production. |
| **Kafka failure non-blocking**           | A Kafka publish failure after a SUCCESS posting should not roll back the posting. Log at ERROR and alert ops, but the posting data is already safely in PostgreSQL.                                                                                                                                  |
| **Transaction scope**                    | `AccountPostingServiceImpl` methods are `@Transactional`. If an unhandled exception bubbles up, the entire create/update rolls back. Pre-inserted legs are also rolled back in this case — a clean state.                                                                                            |
