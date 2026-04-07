import client from './client';
import type { DashboardSummary } from './types';

export const dashboardApi = {
  summary: async () => (await client.get<DashboardSummary>('/dashboard/summary')).data,
};
