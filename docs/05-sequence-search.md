# Sequence Diagram — Search / List Postings Flow

Sequence for `GET /account-posting` with optional query parameters. Highlights the dynamic JPA Specification predicate
building and paginated response construction.

---

## Search Flow

```mermaid
sequenceDiagram
    autonumber

    participant Client as Caller / UI
    participant MDC as MdcLoggingFilter
    participant Ctrl as AccountPostingController
    participant Svc as AccountPostingService
    participant Spec as AccountPostingSpecification
    participant PostRepo as AccountPostingRepository<br/>(JpaSpecificationExecutor)
    participant LegSvc as AccountPostingLegService
    participant LegRepo as AccountPostingLegRepository
    participant Mapper as AccountPostingMapper

    Client->>MDC: GET /account-posting?status=PENDING&requestType=INTRA_BANK<br/>&sourceName=PAYMENT_HUB&page=0&size=20&sort=createdAt,desc
    MDC->>MDC: Seed MDC traceId
    MDC->>Ctrl: Forward

    Ctrl->>Ctrl: Bind query params →<br/>AccountPostingSearchRequest {<br/>  status=PENDING,<br/>  requestType=INTRA_BANK,<br/>  sourceName=PAYMENT_HUB,<br/>  page=0, size=20<br/>}
    Ctrl->>Svc: searchPostings(searchRequest, Pageable)

    Note over Svc,Spec: Build dynamic JPA Criteria predicates
    Svc->>Spec: AccountPostingSpecification.from(searchRequest)
    Spec->>Spec: List<Predicate> predicates = []

    alt status is not null
        Spec->>Spec: predicates.add(cb.equal(root.get("status"), PENDING))
    end
    alt requestType is not null
        Spec->>Spec: predicates.add(cb.equal(root.get("requestType"), "INTRA_BANK"))
    end
    alt sourceName is not null
        Spec->>Spec: predicates.add(cb.equal(root.get("sourceName"), "PAYMENT_HUB"))
    end
    alt sourceReferenceId is not null
        Spec->>Spec: predicates.add(cb.equal(root.get("sourceReferenceId"), ...))
    end
    alt endToEndReferenceId is not null
        Spec->>Spec: predicates.add(cb.equal(root.get("endToEndReferenceId"), ...))
    end
    alt dateFrom is not null
        Spec->>Spec: predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom))
    end
    alt dateTo is not null
        Spec->>Spec: predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo))
    end
    alt amountMin is not null
        Spec->>Spec: predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), amountMin))
    end
    alt amountMax is not null
        Spec->>Spec: predicates.add(cb.lessThanOrEqualTo(root.get("amount"), amountMax))
    end

    Spec->>Spec: return cb.and(predicates.toArray())
    Spec-->>Svc: Specification<AccountPosting>

    Note over Svc,PostRepo: Execute paginated query
    Svc->>PostRepo: findAll(specification, pageable)
    Note right of PostRepo: Spring Data generates:<br/>SELECT * FROM account_posting<br/>WHERE status='PENDING'<br/>AND request_type='INTRA_BANK'<br/>AND source_name='PAYMENT_HUB'<br/>ORDER BY created_at DESC<br/>LIMIT 20 OFFSET 0
    PostRepo-->>Svc: Page<AccountPosting> { content=[...], totalElements=47 }

    Note over Svc,LegSvc: Fetch legs for each posting in the page
    loop for each AccountPosting in page.content
        Svc->>LegSvc: getLegsByPostingId(postingId)
        LegSvc->>LegRepo: findByPostingIdOrderByLegOrder(postingId)
        LegRepo-->>LegSvc: List<AccountPostingLeg>
        LegSvc-->>Svc: List<AccountPostingLegResponse>
    end

    Note over Svc,Mapper: Map to response DTOs
    Svc->>Mapper: toResponse(posting, legs) for each posting
    Mapper-->>Svc: List<AccountPostingResponse>

    Svc->>Svc: Wrap in PageResponse {<br/>  content, page, size,<br/>  totalElements=47, totalPages=3<br/>}
    Svc-->>Ctrl: PageResponse<AccountPostingResponse>

    Ctrl-->>Client: 200 OK {<br/>  success: true,<br/>  data: {<br/>    content: [...],<br/>    page: 0, size: 20,<br/>    totalElements: 47,<br/>    totalPages: 3<br/>  }<br/>}
```

---

## Search Parameter Reference

```mermaid
flowchart LR
    subgraph Query_Params["Query Parameters (all optional)"]
        P1[status]
        P2[requestType]
        P3[sourceName]
        P4[sourceReferenceId]
        P5[endToEndReferenceId]
        P6[dateFrom]
        P7[dateTo]
        P8[amountMin]
        P9[amountMax]
        P10[page / size / sort]
    end

    subgraph Spec["AccountPostingSpecification"]
        S1[cb.equal status]
        S2[cb.equal requestType]
        S3[cb.equal sourceName]
        S4[cb.equal sourceRefId]
        S5[cb.equal e2eRefId]
        S6[cb.greaterThanOrEqualTo createdAt]
        S7[cb.lessThanOrEqualTo createdAt]
        S8[cb.greaterThanOrEqualTo amount]
        S9[cb.lessThanOrEqualTo amount]
    end

    subgraph Result["SQL WHERE clause"]
        R1["cb.and(all predicates)"]
    end

    P1 -- if present --> S1
    P2 -- if present --> S2
    P3 -- if present --> S3
    P4 -- if present --> S4
    P5 -- if present --> S5
    P6 -- if present --> S6
    P7 -- if present --> S7
    P8 -- if present --> S8
    P9 -- if present --> S9

    S1 & S2 & S3 & S4 & S5 & S6 & S7 & S8 & S9 --> R1
```

---

## Key Notes

| Aspect                       | Detail                                                                                                                                                                                     |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Zero parameters**          | If no filter params are provided, `predicates` is empty and `cb.and()` with no args evaluates to `TRUE` — returns all postings (paginated)                                                 |
| **JpaSpecificationExecutor** | `AccountPostingRepository` extends `JpaSpecificationExecutor<AccountPosting>`, enabling `findAll(Specification, Pageable)` without writing a custom query                                  |
| **N+1 avoidance**            | Legs are fetched in a loop per posting (one query per posting in the page). For pages of 20 this is acceptable; a batch-fetch or `IN (postingIds)` query could be added as an optimisation |
| **Sorting**                  | Spring Data `Pageable` handles `sort=createdAt,desc`. Multiple sort fields are supported.                                                                                                  |
| **UI integration**           | The React `PostingListPage` sends search form values as query params and renders the paginated `PageResponse`                                                                              |
