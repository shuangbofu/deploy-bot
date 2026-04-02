import client from './client';
import type { DeploymentPayload, DeploymentSummary, LogResponse } from './types';

/**
 * 部署记录与部署动作接口。
 */
export const deploymentsApi = {
  list: async () => (await client.get<DeploymentSummary[]>('/deployments')).data,
  listMine: async () => (await client.get<DeploymentSummary[]>('/deployments/mine')).data,
  detail: async (id: number | string) => (await client.get<DeploymentSummary>(`/deployments/${id}`)).data,
  getLog: async (id: number | string) => (await client.get<LogResponse>(`/deployments/${id}/log`)).data,
  create: async (payload: DeploymentPayload) => (await client.post<DeploymentSummary>('/deployments', payload)).data,
  stop: async (id: number | string) => (await client.post<DeploymentSummary>(`/deployments/${id}/stop`)).data,
  rollback: async (id: number | string) => (await client.post<DeploymentSummary>(`/deployments/${id}/rollback`)).data,
};
