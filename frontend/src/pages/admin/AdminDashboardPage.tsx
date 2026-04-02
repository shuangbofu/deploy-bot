import { Card, Col, Empty, Progress, Row, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import DashboardConsole from '../../components/DashboardConsole';
import { ACTIVE_DEPLOYMENT_STATUSES, FINISHED_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import { deploymentsApi } from '../../api/deployments';
import { hostsApi } from '../../api/hosts';
import { pipelinesApi } from '../../api/pipelines';
import { projectsApi } from '../../api/projects';
import { servicesApi } from '../../api/services';
import { templatesApi } from '../../api/templates';
import { usersApi } from '../../api/users';
import type { DeploymentSummary, HostResourceSnapshot, HostSummary, PipelineSummary, ProjectSummary, ServiceSummary, TemplateSummary, UserSummary } from '../../types/domain';

interface DashboardData {
  projects: ProjectSummary[];
  templates: TemplateSummary[];
  pipelines: PipelineSummary[];
  deployments: DeploymentSummary[];
  hosts: HostSummary[];
  services: ServiceSummary[];
  resources: HostResourceSnapshot[];
  users: UserSummary[];
}

export default function AdminDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<DashboardData>({
    projects: [],
    templates: [],
    pipelines: [],
    deployments: [],
    hosts: [],
    services: [],
    resources: [],
    users: [],
  });

  const load = async () => {
    setLoading(true);
    try {
      const [projects, templates, pipelines, deployments, hosts, services, users] = await Promise.all([
        projectsApi.list(),
        templatesApi.list(),
        pipelinesApi.list(),
        deploymentsApi.list(),
        hostsApi.list(),
        servicesApi.list(),
        usersApi.list(),
      ]);
      const resources = await Promise.all(
        hosts.map((host) => hostsApi.previewResources(host.id).catch(() => ({
          hostId: host.id,
          hostName: host.name,
          workspaceRoot: host.workspaceRoot,
          preview: '资源采集失败',
        } as HostResourceSnapshot))),
      );
      setData({ projects, templates, pipelines, deployments, hosts, services, resources, users });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load().catch(() => message.error('加载控制台数据失败'));
  }, []);

  const formatMemoryMb = (value?: number | null) => {
    if (value == null) return '-';
    if (value >= 1024) return `${(value / 1024).toFixed(value >= 10240 ? 0 : 1)} GB`;
    return `${value} MB`;
  };

  const stats = useMemo(() => {
    const running = data.deployments.filter((item) => item.status && ACTIVE_DEPLOYMENT_STATUSES.includes(item.status)).length;
    const success = data.deployments.filter((item) => item.status === 'SUCCESS').length;
    const totalFinished = data.deployments.filter((item) => item.status && FINISHED_DEPLOYMENT_STATUSES.includes(item.status)).length;
    const successRate = totalFinished > 0 ? Math.round((success * 100) / totalFinished) : 0;
    return {
      projects: data.projects.length,
      templates: data.templates.length,
      pipelines: data.pipelines.length,
      deployments: data.deployments.length,
      hosts: data.hosts.length,
      services: data.services.length,
      users: data.users.length,
      runningServices: data.services.filter((item) => item.status === 'RUNNING').length,
      successRate,
    };
  }, [data]);

  return (
    <DashboardConsole
      title="控制台"
      description="查看平台概览、部署趋势、最近执行记录和异常情况。"
      loading={loading}
      stats={stats}
      deployments={data.deployments}
      services={data.services}
      resources={data.resources}
      isAdmin
      detailBasePath="/admin/deployments"
      listPath="/admin/deployments"
      backFrom="/admin/dashboard"
      backLabel="返回控制台"
      idPrefix="admin-dashboard"
    />
  );
}
