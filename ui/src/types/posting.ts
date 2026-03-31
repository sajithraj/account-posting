export type PostingStatus = 'PNDG' | 'ACSP' | 'RJCT';
export type CreditDebitIndicator = 'CREDIT' | 'DEBIT';

export interface AccountPostingRequest {
    sourceReferenceId: string;
    endToEndReferenceId: string;
    sourceName: string;
    requestType: string;
    amount: number;
    currency: string;
    creditDebitIndicator: CreditDebitIndicator;
    debtorAccount: string;
    creditorAccount: string;
    requestedExecutionDate: string; // ISO date yyyy-MM-dd
    remittanceInformation?: string;
}

export interface LegResponse {
    postingLegId: number;
    legOrder: number;
    name: string;       // CBS / GL / OBPM
    type: string;       // POSTING / ADD_HOLD / CANCEL_HOLD
    account: string;
    referenceId?: string;
    postedTime?: string;
    status: string;
    reason?: string;
    mode: string;
}

export interface AccountPostingResponse {
    postingId: number;
    sourceReferenceId: string;
    endToEndReferenceId: string;
    sourceName: string;
    requestType: string;
    amount: number;
    currency: string;
    creditDebitIndicator: string;
    debtorAccount: string;
    creditorAccount: string;
    requestedExecutionDate: string;
    remittanceInformation?: string;
    postingStatus: PostingStatus;
    targetSystems?: string;
    reason?: string;
    processedAt?: string;
    createdAt: string;
    updatedAt: string;
    responses?: LegResponse[];
}

// ── Governance-compliant search types ────────────────────────────────────────

export interface SearchCondition {
    property: string;
    operator: string;
    values: string[];
}

export interface SearchOrderBy {
    property: string;
    sortOrder: string;
}

export interface SearchPagination {
    offset: number;
    limit: number;
}

export interface PostingSearchRequest {
    projectedProperties?: string[];
    conditions?: SearchCondition[];
    orderBy?: SearchOrderBy[];
    pagination?: SearchPagination;
}

export interface PostingSearchResponse<T> {
    items: T[];
    totalItems: number;
    offset: number;
    limit: number;
}

// UI-internal filter draft (flat, for form inputs)
export interface PostingFilterDraft {
    status?: PostingStatus;
    endToEndReferenceId?: string;
    sourceReferenceId?: string;
    sourceName?: string;
    requestType?: string;
    targetSystem?: string;
    fromDate?: string;
    toDate?: string;
}

export interface PostingConfigResponse {
    configId: number;
    sourceName: string;
    requestType: string;
    targetSystem: string;
    operation: string;
    orderSeq: number;
}

export interface PostingConfigRequest {
    sourceName: string;
    requestType: string;
    targetSystem: string;
    operation: string;
    orderSeq: number;
}

export interface ApiErrorResponse {
    id: string;
    name: string;
    message: string;
    errors?: { field: string; message: string; rejectedValue?: unknown }[];
}
