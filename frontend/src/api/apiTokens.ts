import client from './client';
import type { ApiTokenCreatePayload, ApiTokenCreateResult, ApiTokenSummary, ApiTokenUpdatePayload } from './types';

export const apiTokensApi = {
  list: async () => (await client.get<ApiTokenSummary[]>('/api-tokens')).data,
  create: async (payload: ApiTokenCreatePayload) => (await client.post<ApiTokenCreateResult>('/api-tokens', payload)).data,
  update: async (id: number, payload: ApiTokenUpdatePayload) => (await client.put<ApiTokenSummary>(`/api-tokens/${id}`, payload)).data,
  revoke: async (id: number) => client.post(`/api-tokens/${id}/revoke`),
};
