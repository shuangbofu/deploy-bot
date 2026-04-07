import { Empty, message } from 'antd';
import { useEffect, useState } from 'react';
import { dashboardApi } from '../../api/dashboard';
import DashboardConsole from '../../components/DashboardConsole';
import type { DashboardSummary } from '../../types/domain';

export default function UserDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState<DashboardSummary>();
  const [tick, setTick] = useState(() => Date.now());

  const loadData = async () => {
    setLoading(true);
    try {
      setSummary(await dashboardApi.summary());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData().catch(() => message.error('加载控制台数据失败'));
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <DashboardConsole
      title="控制台"
      description="查看可用流水线、近期部署趋势和当前部署情况。"
      loading={loading}
      stats={summary?.stats ?? {
        projects: 0,
        templates: 0,
        pipelines: 0,
        deployments: 0,
        hosts: 0,
        services: 0,
        users: 0,
        runningServices: 0,
        successRate: 0,
        runningDeployments: 0,
        failedDeployments: 0,
      }}
      trend={summary?.trend ?? []}
      latestDeployments={summary?.latestDeployments ?? []}
      attentionDeployments={summary?.attentionDeployments ?? []}
      services={summary?.services ?? []}
      resources={[]}
      isAdmin={false}
      detailBasePath="/user/deployments"
      listPath="/user/deployments"
      backFrom="/user/dashboard"
      backLabel="返回控制台"
      idPrefix="user-dashboard"
      tick={tick}
    />
  );
}
