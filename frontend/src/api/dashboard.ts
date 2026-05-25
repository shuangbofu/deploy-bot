import client from './client';
import type { DashboardAnalytics, DashboardAnalyticsQuery } from './types';

export const dashboardApi = {
  analytics: async (params: DashboardAnalyticsQuery) => (await client.get<DashboardAnalytics>('/dashboard/analytics', { params })).data,
};
