import { Empty, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { deploymentsApi } from '../../api/deployments';
import { pipelinesApi } from '../../api/pipelines';
import DashboardConsole from '../../components/DashboardConsole';
import { ACTIVE_DEPLOYMENT_STATUSES, FINISHED_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import type { DeploymentSummary, PipelineSummary } from '../../types/domain';

export default function UserDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [pipelines, setPipelines] = useState<PipelineSummary[]>([]);
  const [deployments, setDeployments] = useState<DeploymentSummary[]>([]);
  const [tick, setTick] = useState(() => Date.now());

  const loadData = async () => {
    setLoading(true);
    try {
      const [nextPipelines, nextDeployments] = await Promise.all([
        pipelinesApi.list(),
        deploymentsApi.listMine(),
      ]);
      setPipelines(nextPipelines);
      setDeployments(nextDeployments);
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

  const stats = useMemo(() => {
    const running = deployments.filter((item) => item.status && ACTIVE_DEPLOYMENT_STATUSES.includes(item.status)).length;
    const success = deployments.filter((item) => item.status === 'SUCCESS').length;
    const failed = deployments.filter((item) => item.status === 'FAILED').length;
    const totalFinished = deployments.filter((item) => item.status && FINISHED_DEPLOYMENT_STATUSES.includes(item.status)).length;
    const successRate = totalFinished > 0 ? Math.round((success * 100) / totalFinished) : 0;
    return {
      projects: 0,
      templates: 0,
      pipelines: pipelines.length,
      deployments: deployments.length,
      hosts: 0,
      services: 0,
      users: 0,
      running,
      failed,
      successRate,
      runningServices: 0,
    };
  }, [deployments, pipelines]);

  return (
    <DashboardConsole
      title="控制台"
      description="查看可用流水线、近期部署趋势和当前部署情况。"
      loading={loading}
      stats={stats}
      deployments={deployments}
      services={[]}
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
