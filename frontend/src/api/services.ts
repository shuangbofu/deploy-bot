import client from './client';
import type { DeploymentSummary, ServicePidHistorySummary, ServiceProcessSummary, ServiceSummary } from './types';

/**
 * 服务管理接口。
 */
export const servicesApi = {
  list: async () => (await client.get<ServiceSummary[]>('/services')).data,
  action: async (id: number, action: 'start' | 'stop' | 'restart') => (
    await client.post<DeploymentSummary | ServiceSummary>(`/services/${id}/${action}`)
  ).data,
  listProcesses: async (id: number) => (await client.get<ServiceProcessSummary[]>(`/services/${id}/processes`)).data,
  listPidHistory: async (id: number) => (await client.get<ServicePidHistorySummary[]>(`/services/${id}/pid-history`)).data,
  bindProcess: async (id: number, pid: number) => (
    await client.post<ServiceSummary>(`/services/${id}/bind-process`, { pid })
  ).data,
};
