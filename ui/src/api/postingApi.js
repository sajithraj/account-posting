import axios, { AxiosError } from 'axios';
// ── key-case helpers ──────────────────────────────────────────────────────────
function snakeToCamel(str) {
    return str.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
}
function camelToSnake(str) {
    return str.replace(/[A-Z]/g, c => `_${c.toLowerCase()}`);
}
function transformKeys(obj, transform) {
    if (Array.isArray(obj))
        return obj.map(v => transformKeys(v, transform));
    if (obj !== null && typeof obj === 'object') {
        return Object.fromEntries(Object.entries(obj).map(([k, v]) => [
            transform(k),
            transformKeys(v, transform),
        ]));
    }
    return obj;
}
// ── axios instance with interceptors ─────────────────────────────────────────
const BASE = '/v2/payment/account-posting';
const http = axios.create({ baseURL: '' });
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
export function getErrorMessage(err) {
    if (err instanceof AxiosError && err.response?.data?.message) {
        return err.response.data.message;
    }
    if (err instanceof Error)
        return err.message;
    return 'An unexpected error occurred';
}
export const postingApi = {
    create: async (request) => {
        const res = await http.post(BASE, request);
        return res.data;
    },
    search: async (params) => {
        const res = await http.get(BASE, { params });
        return res.data;
    },
    getById: async (postingId) => {
        const res = await http.get(`${BASE}/${postingId}`);
        return res.data;
    },
    retry: async (postingIds) => {
        const res = await http.post(`${BASE}/retry`, { postingIds });
        return res.data;
    },
    getAllConfigs: async () => {
        const res = await http.get(`${BASE}/config`);
        return res.data;
    },
    getConfig: async (requestType) => {
        const res = await http.get(`${BASE}/config/${requestType}`);
        return res.data;
    },
    createConfig: async (request) => {
        const res = await http.post(`${BASE}/config`, request);
        return res.data;
    },
    updateConfig: async (configId, request) => {
        const res = await http.put(`${BASE}/config/${configId}`, request);
        return res.data;
    },
    deleteConfig: async (configId) => {
        await http.delete(`${BASE}/config/${configId}`);
    },
    flushConfigCache: async () => {
        await http.post(`${BASE}/config/cache/flush`);
    },
    updateLegStatus: async (postingId, postingLegId, status, reason) => {
        const res = await http.patch(`${BASE}/${postingId}/leg/${postingLegId}`, { status, ...(reason ? { reason } : {}) });
        return res.data;
    },
};
