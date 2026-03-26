import client from './client';
import type { DeploymentSummary, ServiceSummary } from './types';

/**
 * 服务管理接口。
 */
export const servicesApi = {
  list: async () => (await client.get<ServiceSummary[]>('/services')).data,
  action: async (id: number, action: 'start' | 'stop' | 'restart') => (
    await client.post<DeploymentSummary | ServiceSummary>(`/services/${id}/${action}`)
  ).data,
};
