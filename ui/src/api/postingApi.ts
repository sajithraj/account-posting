import axios, {AxiosError} from 'axios';
import type {
    AccountPostingRequest,
    AccountPostingResponse,
    PostingConfigRequest,
    PostingConfigResponse,
    PostingCreateResponse,
    PostingSearchRequest,
    PostingSearchResponse,
} from '../types/posting';

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

const BASE = '/v3/payment/account-posting';

const http = axios.create({baseURL: ''});

http.interceptors.request.use(config => {
    if (config.data && typeof config.data === 'object') {
        config.data = transformKeys(config.data, camelToSnake);
    }
    return config;
});

http.interceptors.response.use(response => {
    response.data = transformKeys(response.data, snakeToCamel);
    return response;
});

export function getErrorMessage(err: unknown): string {
    if (err instanceof AxiosError) {
        if (err.response?.data?.error?.message) {
            return err.response.data.error.message;
        }
        if (err.response?.data?.message) {
            return err.response.data.message;
        }
    }
    if (err instanceof Error) return err.message;
    return 'An unexpected error occurred';
}

export const postingApi = {
    create: async (request: AccountPostingRequest): Promise<PostingCreateResponse> => {
        const res = await http.post<PostingCreateResponse>(BASE, request);
        return res.data;
    },

    search: async (request: PostingSearchRequest): Promise<PostingSearchResponse> => {
        const res = await http.post<PostingSearchResponse>(`${BASE}/search`, request);
        return res.data;
    },

    getById: async (postingId: string): Promise<AccountPostingResponse> => {
        const res = await http.get<AccountPostingResponse>(`${BASE}/${postingId}`);
        return res.data;
    },

    retry: async (postingIds?: string[]): Promise<unknown> => {
        const res = await http.post<unknown>(`${BASE}/retry`, {
            postingIds,
            requestedBy: 'OPS-USER',
        });
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

    updateConfig: async (requestType: string, orderSeq: number, request: PostingConfigRequest): Promise<PostingConfigResponse> => {
        const res = await http.put<PostingConfigResponse>(
            `${BASE}/config/${requestType}/${orderSeq}`,
            request,
        );
        return res.data;
    },

    deleteConfig: async (requestType: string, orderSeq: number): Promise<void> => {
        await http.delete(`${BASE}/config/${requestType}/${orderSeq}`);
    },

    updateLegStatus: async (
        postingId: string,
        transactionOrder: number,
        status: string,
        reason?: string,
    ): Promise<unknown> => {
        const res = await http.patch<unknown>(
            `${BASE}/${postingId}/transaction/${transactionOrder}`,
            {status, requestedBy: 'OPS-USER', ...(reason ? {reason} : {})},
        );
        return res.data;
    },
};
