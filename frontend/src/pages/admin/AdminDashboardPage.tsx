import { useEffect, useState } from 'react';
import { message } from 'antd';
import { dashboardApi } from '../../api/dashboard';
import { hostsApi } from '../../api/hosts';
import DashboardConsole from '../../components/DashboardConsole';
import type { DashboardAnalytics, DashboardAnalyticsQuery, HostResourceSnapshot } from '../../types/domain';
import { getRequestErrorMessage } from '../../utils/requestError';

export default function AdminDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [analytics, setAnalytics] = useState<DashboardAnalytics>();
  const [resources, setResources] = useState<HostResourceSnapshot[]>([]);
  const [query, setQuery] = useState<DashboardAnalyticsQuery>({ range: '30d', granularity: 'day' });

  const load = async (nextQuery = query) => {
    setLoading(true);
    try {
      const [nextAnalytics, hosts] = await Promise.all([
        dashboardApi.analytics(nextQuery),
        hostsApi.list(true),
      ]);
      setAnalytics(nextAnalytics);
      setResources(await Promise.all(hosts.map(async (host) => {
        try {
          return await hostsApi.previewResources(host.id);
        } catch (error) {
          return {
            hostId: host.id,
            hostName: host.name,
            osType: host.type === 'LOCAL' ? 'LOCAL' : 'SSH',
            workspaceRoot: host.workspaceRoot,
            errorMessage: getRequestErrorMessage(error, '资源采集失败'),
          } satisfies HostResourceSnapshot;
        }
      })));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load().catch((error) => message.error(getRequestErrorMessage(error, '加载仪表盘失败')));
  }, [query]);

  return (
    <DashboardConsole
      title="仪表盘"
      description="查看部署趋势、状态分布、维度排行和耗时分析。"
      loading={loading}
      analytics={analytics}
      resources={resources}
      query={query}
      onQueryChange={setQuery}
      isAdmin
    />
  );
}
