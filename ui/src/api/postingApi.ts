import axios, {AxiosError} from 'axios';
import type {
    AccountPostingRequest,
    AccountPostingResponse,
    PostingConfigRequest,
    PostingConfigResponse,
    PostingSearchRequest,
    PostingSearchResponse,
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

// ── axios instance with interceptors ─────────────────────────────────────────

const BASE = '/v2/payment/account-posting';

const http = axios.create({baseURL: ''});

// Outgoing: convert camelCase body → snake_case (POST / PUT / PATCH only)
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

// Extract a human-readable message from any API error
export function getErrorMessage(err: unknown): string {
    if (err instanceof AxiosError && err.response?.data?.message) {
        return err.response.data.message;
    }
    if (err instanceof Error) return err.message;
    return 'An unexpected error occurred';
}

export const postingApi = {
    create: async (request: AccountPostingRequest): Promise<AccountPostingResponse> => {
        const res = await http.post<AccountPostingResponse>(BASE, request);
        return res.data;
    },

    search: async (request: PostingSearchRequest): Promise<PostingSearchResponse<AccountPostingResponse>> => {
        const res = await http.post<PostingSearchResponse<AccountPostingResponse>>(`${BASE}/search`, request);
        return res.data;
    },

    getById: async (postingId: number): Promise<AccountPostingResponse> => {
        const res = await http.get<AccountPostingResponse>(`${BASE}/${postingId}`);
        return res.data;
    },

    retry: async (postingIds?: number[]): Promise<unknown> => {
        const res = await http.post<unknown>(`${BASE}/retry`, {postingIds, requestedBy: 'OPS-USER'});
        return res.data;
    },

    getAllConfigs: async (): Promise<PostingConfigResponse[]> => {
        const res = await http.get<PostingConfigResponse[]>(`${BASE}/config`);
        return res.data;
    },

    getConfig: async (requestType: string): Promise<PostingConfigResponse[]> => {
        const res = await http.get<PostingConfigResponse[]>(`${BASE}/config/${requestType}`);
        return res.data;
    },

    createConfig: async (request: PostingConfigRequest): Promise<PostingConfigResponse> => {
        const res = await http.post<PostingConfigResponse>(`${BASE}/config`, request);
        return res.data;
    },

    updateConfig: async (configId: number, request: PostingConfigRequest): Promise<PostingConfigResponse> => {
        const res = await http.put<PostingConfigResponse>(`${BASE}/config/${configId}`, request);
        return res.data;
    },

    deleteConfig: async (configId: number): Promise<void> => {
        await http.delete(`${BASE}/config/${configId}`);
    },

    updateLegStatus: async (
        postingId: number,
        postingLegId: number,
        status: string,
        reason?: string,
    ): Promise<unknown> => {
        const res = await http.patch<unknown>(
            `${BASE}/${postingId}/leg/${postingLegId}`,
            {status, requestedBy: 'OPS-USER', ...(reason ? {reason} : {})},
        );
        return res.data;
    },
};
