import { Empty, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { deploymentsApi } from '../../api/deployments';
import { hostsApi } from '../../api/hosts';
import { pipelinesApi } from '../../api/pipelines';
import { projectsApi } from '../../api/projects';
import { servicesApi } from '../../api/services';
import { templatesApi } from '../../api/templates';
import { usersApi } from '../../api/users';
import DashboardConsole from '../../components/DashboardConsole';
import { ACTIVE_DEPLOYMENT_STATUSES, FINISHED_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import type { DeploymentSummary, HostSummary, PipelineSummary, ProjectSummary, ServiceSummary, TemplateSummary, UserSummary } from '../../types/domain';

export default function UserDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [templates, setTemplates] = useState<TemplateSummary[]>([]);
  const [pipelines, setPipelines] = useState<PipelineSummary[]>([]);
  const [deployments, setDeployments] = useState<DeploymentSummary[]>([]);
  const [hosts, setHosts] = useState<HostSummary[]>([]);
  const [services, setServices] = useState<ServiceSummary[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [tick, setTick] = useState(() => Date.now());

  const loadData = async () => {
    setLoading(true);
    try {
      const [nextProjects, nextTemplates, nextPipelines, nextDeployments, nextHosts, nextServices, nextUsers] = await Promise.all([
        projectsApi.list(),
        templatesApi.list(),
        pipelinesApi.list(),
        deploymentsApi.list(),
        hostsApi.list(),
        servicesApi.list(),
        usersApi.list(),
      ]);
      setProjects(nextProjects);
      setTemplates(nextTemplates);
      setPipelines(nextPipelines);
      setDeployments(nextDeployments);
      setHosts(nextHosts);
      setServices(nextServices);
      setUsers(nextUsers);
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
      projects: projects.length,
      templates: templates.length,
      pipelines: pipelines.length,
      deployments: deployments.length,
      hosts: hosts.length,
      services: services.length,
      users: users.length,
      running,
      failed,
      successRate,
      runningServices: services.filter((item) => item.status === 'RUNNING').length,
    };
  }, [deployments, hosts, pipelines, projects, services, templates, users]);

  return (
    <DashboardConsole
      title="控制台"
      description="查看可用流水线、近期部署趋势和当前部署情况。"
      loading={loading}
      stats={stats}
      deployments={deployments}
      services={services}
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
