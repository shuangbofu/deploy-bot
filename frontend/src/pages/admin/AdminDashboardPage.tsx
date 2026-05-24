import { message } from 'antd';
import { useEffect, useState } from 'react';
import { dashboardApi } from '../../api/dashboard';
import DashboardConsole from '../../components/DashboardConsole';
import { hostsApi } from '../../api/hosts';
import type { DashboardSummary, HostResourceSnapshot, HostSummary } from '../../types/domain';

type HostResourceView = HostResourceSnapshot & { loading?: boolean };

interface DashboardData {
  summary?: DashboardSummary;
  hosts: HostSummary[];
  resources: HostResourceView[];
}

export default function AdminDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [resourceLoading, setResourceLoading] = useState(false);
  const [data, setData] = useState<DashboardData>({
    hosts: [],
    resources: [],
  });

  const load = async () => {
    setLoading(true);
    try {
      const summary = await dashboardApi.summary();
      setData((previous) => ({
        ...previous,
        summary,
      }));
    } finally {
      setLoading(false);
    }
    void loadHostsAndResources();
  };

  const loadHostsAndResources = async () => {
    try {
      const hosts = await hostsApi.list();
      setData((previous) => ({ ...previous, hosts }));
      void loadResources(hosts);
    } catch {
      message.error('加载主机资源失败');
    }
  };

  const loadResources = async (hosts: HostSummary[]) => {
    setResourceLoading(true);
    if (hosts.length === 0) {
      setData((previous) => ({ ...previous, resources: [] }));
      setResourceLoading(false);
      return;
    }
    setData((previous) => ({
      ...previous,
      resources: hosts.map((host) => ({
        hostId: host.id,
        hostName: host.name,
        workspaceRoot: host.workspaceRoot,
        loading: true,
      })),
    }));

    let pending = hosts.length;
    hosts.forEach((host) => {
      hostsApi.previewResources(host.id)
        .then((resource) => {
          setData((previous) => ({
            ...previous,
            resources: previous.resources.map((item) => (item.hostId === host.id ? { ...resource, loading: false } : item)),
          }));
        })
        .catch(() => {
          setData((previous) => ({
            ...previous,
            resources: previous.resources.map((item) => (item.hostId === host.id ? {
              hostId: host.id,
              hostName: host.name,
              workspaceRoot: host.workspaceRoot,
              preview: '资源采集失败',
              loading: false,
            } : item)),
          }));
        })
        .finally(() => {
          pending -= 1;
          if (pending <= 0) {
            setResourceLoading(false);
          }
        });
    });
  };

  useEffect(() => {
    load().catch(() => message.error('加载仪表盘数据失败'));
  }, []);

  return (
    <DashboardConsole
      title="仪表盘"
      description="查看平台概览、部署趋势、最近部署记录和异常情况。"
      loading={loading}
      resourceLoading={resourceLoading}
      stats={data.summary?.stats ?? {
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
      trend={data.summary?.trend ?? []}
      latestDeployments={data.summary?.latestDeployments ?? []}
      attentionDeployments={data.summary?.attentionDeployments ?? []}
      services={data.summary?.services ?? []}
      resources={data.resources}
      isAdmin
      detailBasePath="/admin/deployments"
      listPath="/admin/deployments"
      backFrom="/admin/dashboard"
      backLabel="返回仪表盘"
      idPrefix="admin-dashboard"
    />
  );
}
