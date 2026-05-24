import { useEffect, useState } from 'react';
import { dashboardApi } from '../../api/dashboard';
import DashboardConsole from '../../components/DashboardConsole';
import type { DashboardAnalytics, DashboardAnalyticsQuery } from '../../types/domain';

export default function UserDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [analytics, setAnalytics] = useState<DashboardAnalytics>();
  const [query, setQuery] = useState<DashboardAnalyticsQuery>({ range: '30d', granularity: 'day' });

  const loadData = async (nextQuery = query) => {
    setLoading(true);
    try {
      setAnalytics(await dashboardApi.analytics(nextQuery));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData().catch(() => undefined);
  }, [query]);

  return (
    <DashboardConsole
      title="仪表盘"
      description="查看你的部署趋势、状态分布、流水线排行和耗时分析。"
      loading={loading}
      analytics={analytics}
      query={query}
      onQueryChange={setQuery}
      isAdmin={false}
    />
  );
}
