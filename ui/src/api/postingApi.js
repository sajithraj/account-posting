import axios, { AxiosError } from 'axios';
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
const BASE = '/v3/payment/account-posting';
const http = axios.create({ baseURL: '' });
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
export function getErrorMessage(err) {
    if (err instanceof AxiosError) {
        if (err.response?.data?.error?.message) {
            return err.response.data.error.message;
        }
        if (err.response?.data?.message) {
            return err.response.data.message;
        }
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
    search: async (request) => {
        const res = await http.post(`${BASE}/search`, request);
        return res.data;
    },
    getById: async (postingId) => {
        const res = await http.get(`${BASE}/${postingId}`);
        return res.data;
    },
    retry: async (postingIds) => {
        const res = await http.post(`${BASE}/retry`, {
            postingIds,
            requestedBy: 'OPS-USER',
        });
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
    updateConfig: async (requestType, orderSeq, request) => {
        const res = await http.put(`${BASE}/config/${requestType}/${orderSeq}`, request);
        return res.data;
    },
    deleteConfig: async (requestType, orderSeq) => {
        await http.delete(`${BASE}/config/${requestType}/${orderSeq}`);
    },
    updateLegStatus: async (postingId, transactionOrder, status, reason) => {
        const res = await http.patch(`${BASE}/${postingId}/transaction/${transactionOrder}`, { status, requestedBy: 'OPS-USER', ...(reason ? { reason } : {}) });
        return res.data;
    },
};
