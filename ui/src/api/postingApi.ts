import axios, { AxiosError } from 'axios';
import type {
  AccountPostingRequest,
  AccountPostingResponse,
  PageResponse,
  PostingConfigRequest,
  PostingConfigResponse,
  PostingSearchParams,
} from '../types/posting';

const http = axios.create({ baseURL: '/api' });

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
    const res = await http.post<AccountPostingResponse>('/account-posting', request);
    return res.data;
  },

  search: async (params: PostingSearchParams): Promise<PageResponse<AccountPostingResponse>> => {
    const res = await http.get<PageResponse<AccountPostingResponse>>('/account-posting', { params });
    return res.data;
  },

  getById: async (postingId: number): Promise<AccountPostingResponse> => {
    const res = await http.get<AccountPostingResponse>(`/account-posting/${postingId}`);
    return res.data;
  },

  retry: async (postingIds?: number[]): Promise<unknown> => {
    const res = await http.post<unknown>('/account-posting/retry', { postingIds });
    return res.data;
  },

  getAllConfigs: async (): Promise<PostingConfigResponse[]> => {
    const res = await http.get<PostingConfigResponse[]>('/account-posting/config');
    return res.data;
  },

  getConfig: async (requestType: string): Promise<PostingConfigResponse[]> => {
    const res = await http.get<PostingConfigResponse[]>(`/account-posting/config/${requestType}`);
    return res.data;
  },

  createConfig: async (request: PostingConfigRequest): Promise<PostingConfigResponse> => {
    const res = await http.post<PostingConfigResponse>('/account-posting/config', request);
    return res.data;
  },

  updateConfig: async (configId: number, request: PostingConfigRequest): Promise<PostingConfigResponse> => {
    const res = await http.put<PostingConfigResponse>(`/account-posting/config/${configId}`, request);
    return res.data;
  },

  deleteConfig: async (configId: number): Promise<void> => {
    await http.delete(`/account-posting/config/${configId}`);
  },

  flushConfigCache: async (): Promise<void> => {
    await http.post('/account-posting/config/cache/flush');
  },

  updateLegStatus: async (postingId: number, postingLegId: number, status: string): Promise<unknown> => {
    const res = await http.patch<unknown>(
      `/account-posting/${postingId}/leg/${postingLegId}`,
      { status },
    );
    return res.data;
  },
};
