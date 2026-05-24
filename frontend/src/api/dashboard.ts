import client from './client';
import type { DashboardAnalytics, DashboardAnalyticsQuery, DashboardSummary } from './types';

export const dashboardApi = {
  summary: async () => (await client.get<DashboardSummary>('/dashboard/summary')).data,
  analytics: async (params: DashboardAnalyticsQuery) => (await client.get<DashboardAnalytics>('/dashboard/analytics', { params })).data,
};
