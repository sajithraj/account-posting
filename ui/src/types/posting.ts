export type PostingStatus = 'PENDING' | 'SUCCESS' | 'FAILED';
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

export interface PostingSearchParams {
  status?: PostingStatus;
  endToEndReferenceId?: string;
  sourceReferenceId?: string;
  sourceName?: string;
  requestType?: string;
  targetSystem?: string;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
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
