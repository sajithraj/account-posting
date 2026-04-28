export type PostingStatus = 'PNDG' | 'ACSP' | 'RJCT' | 'RCVD';
export type CreditDebitIndicator = 'CREDIT' | 'DEBIT';

export interface Amount {
    value: string;
    currencyCode: string;  // camelToSnake transformer sends this as currency_code
}

export interface AccountPostingRequest {
    sourceReferenceId: string;
    endToEndReferenceId: string;
    sourceName: string;
    requestType: string;
    amount: Amount;
    creditDebitIndicator: CreditDebitIndicator;
    debtorAccount: string;
    creditorAccount: string;
    requestedExecutionDate: string;
    remittanceInformation?: string;
}

export interface PostingCreateResponse {
    postingStatus: string;
    endToEndReferenceId: string;
    sourceReferenceId: string;
    processedAt: string;
}

export interface LegResponse {
    postingId: string;
    transactionOrder: number;
    targetSystem: string;
    operation: string;
    account: string;
    referenceId?: string;
    postedTime?: string;
    status: string;
    reason?: string;
    mode: string;
    attemptNumber?: number;
    createdAt?: string;
    updatedAt?: string;
}

export interface AccountPostingResponse {
    postingId: string;
    sourceReferenceId: string;
    endToEndReferenceId: string;
    sourceName: string;
    requestType: string;
    amount: string;
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
    legs?: LegResponse[];
}

export interface PostingSearchRequest {
    status?: string;
    sourceName?: string;
    requestType?: string;
    endToEndReferenceId?: string;
    sourceReferenceId?: string;
    fromDate?: string;
    toDate?: string;
    limit?: number;
    pageToken?: string;
}

export interface PostingSearchResponse {
    items: AccountPostingResponse[];
    nextPageToken?: string;
}

export interface PostingFilterDraft {
    status?: PostingStatus;
    endToEndReferenceId?: string;
    sourceReferenceId?: string;
    sourceName?: string;
    requestType?: string;
    fromDate?: string;
    toDate?: string;
}

export interface PostingConfigResponse {
    requestType: string;
    orderSeq: number;
    sourceName: string;
    targetSystem: string;
    operation: string;
    processingMode: string;
    createdAt?: string;
    updatedAt?: string;
}

export interface PostingConfigRequest {
    sourceName: string;
    requestType: string;
    targetSystem: string;
    operation: string;
    orderSeq: number;
    processingMode: string;
}

export interface ApiErrorResponse {
    name: string;
    message: string;
}
