import client from './client';
import type { HostConnectionTestResult, HostPayload, HostResourceSnapshot, HostSummary } from './types';

/**
 * 主机相关接口。
 */
export const hostsApi = {
  list: async (enabledOnly = false) => (await client.get<HostSummary[]>('/hosts', { params: enabledOnly ? { enabledOnly } : undefined })).data,
  create: async (payload: HostPayload) => (await client.post<HostSummary>('/hosts', payload)).data,
  update: async (id: number, payload: HostPayload) => (await client.put<HostSummary>(`/hosts/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/hosts/${id}`),
  testConnection: async (id: number) => (await client.post<HostConnectionTestResult>(`/hosts/${id}/test-connection`)).data,
  previewResources: async (id: number) => (await client.get<HostResourceSnapshot>(`/hosts/${id}/resources`)).data,
};
