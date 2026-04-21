import axios, {AxiosError} from 'axios';
import type {
    AccountPostingRequest,
    AccountPostingResponse,
    PostingConfigRequest,
    PostingConfigResponse,
    PostingCreateResponse,
    PostingSearchRequest,
} from '../types/posting';

// ── key-case helpers ──────────────────────────────────────────────────────────

function snakeToCamel(str: string): string {
    return str.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
}

function camelToSnake(str: string): string {
    return str.replace(/[A-Z]/g, c => `_${c.toLowerCase()}`);
}

function transformKeys(obj: unknown, transform: (k: string) => string): unknown {
    if (Array.isArray(obj)) return obj.map(v => transformKeys(v, transform));
    if (obj !== null && typeof obj === 'object') {
        return Object.fromEntries(
            Object.entries(obj as Record<string, unknown>).map(([k, v]) => [
                transform(k),
                transformKeys(v, transform),
            ]),
        );
    }
    return obj;
}

// ── Lambda API envelope ───────────────────────────────────────────────────────

interface ApiEnvelope<T> {
    success: boolean;
    data: T;
    error?: { name: string; message: string } | null;
}

function unwrap<T>(envelope: ApiEnvelope<T>): T {
    if (!envelope.success) {
        throw new Error(envelope.error?.message ?? 'Request failed');
    }
    return envelope.data;
}

// ── axios instance with interceptors ─────────────────────────────────────────

const BASE = '/v3/payment/account-posting';

const http = axios.create({baseURL: ''});

// Outgoing: convert camelCase body → snake_case
http.interceptors.request.use(config => {
    if (config.data && typeof config.data === 'object') {
        config.data = transformKeys(config.data, camelToSnake);
    }
    return config;
});

// Incoming: convert snake_case response → camelCase
http.interceptors.response.use(response => {
    response.data = transformKeys(response.data, snakeToCamel);
    return response;
});

export function getErrorMessage(err: unknown): string {
    if (err instanceof AxiosError && err.response?.data?.error?.message) {
        return err.response.data.error.message;
    }
    if (err instanceof Error) return err.message;
    return 'An unexpected error occurred';
}

export const postingApi = {
    create: async (request: AccountPostingRequest): Promise<PostingCreateResponse> => {
        const res = await http.post<ApiEnvelope<PostingCreateResponse>>(BASE, request);
        return unwrap(res.data);
    },

    search: async (request: PostingSearchRequest): Promise<AccountPostingResponse[]> => {
        const res = await http.post<ApiEnvelope<AccountPostingResponse[]>>(`${BASE}/search`, request);
        return unwrap(res.data);
    },

    getById: async (postingId: number): Promise<AccountPostingResponse> => {
        const res = await http.get<ApiEnvelope<AccountPostingResponse>>(`${BASE}/${postingId}`);
        return unwrap(res.data);
    },

    retry: async (postingIds?: number[]): Promise<unknown> => {
        const res = await http.post<ApiEnvelope<unknown>>(`${BASE}/retry`, {
            postingIds,
            requestedBy: 'OPS-USER',
        });
        return unwrap(res.data);
    },

    getAllConfigs: async (): Promise<PostingConfigResponse[]> => {
        const res = await http.get<ApiEnvelope<PostingConfigResponse[]>>(`${BASE}/config`);
        return unwrap(res.data);
    },

    getConfig: async (requestType: string): Promise<PostingConfigResponse[]> => {
        const res = await http.get<ApiEnvelope<PostingConfigResponse[]>>(`${BASE}/config/${requestType}`);
        return unwrap(res.data);
    },

    createConfig: async (request: PostingConfigRequest): Promise<PostingConfigResponse> => {
        const res = await http.post<ApiEnvelope<PostingConfigResponse>>(`${BASE}/config`, request);
        return unwrap(res.data);
    },

    updateConfig: async (requestType: string, orderSeq: number, request: PostingConfigRequest): Promise<PostingConfigResponse> => {
        const res = await http.put<ApiEnvelope<PostingConfigResponse>>(
            `${BASE}/config/${requestType}/${orderSeq}`,
            request,
        );
        return unwrap(res.data);
    },

    deleteConfig: async (requestType: string, orderSeq: number): Promise<void> => {
        await http.delete(`${BASE}/config/${requestType}/${orderSeq}`);
    },

    updateLegStatus: async (
        postingId: number,
        transactionOrder: number,
        status: string,
        reason?: string,
    ): Promise<unknown> => {
        const res = await http.patch<ApiEnvelope<unknown>>(
            `${BASE}/${postingId}/transaction/${transactionOrder}`,
            {status, requestedBy: 'OPS-USER', ...(reason ? {reason} : {})},
        );
        return unwrap(res.data);
    },
};
