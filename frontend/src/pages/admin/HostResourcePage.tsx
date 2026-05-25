import { Cpu, Database, Gauge, HardDrive, Memory, Pulse, TreeStructure } from '@phosphor-icons/react';
import { Button, Card, Progress, Space, Statistic, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { hostsApi } from '../../api/hosts';
import { runtimeEnvironmentsApi } from '../../api/runtimeEnvironments';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import type { HostResourceSnapshot, RuntimeEnvironmentSummary } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';
import { getRuntimeEnvironmentTypeLabel, sortRuntimeEnvironmentTypes } from '../../utils/runtimeEnvironment';

const percentValue = (value?: number | null) => Math.max(0, Math.min(100, value ?? 0));

const resourceTone = (value?: number | null) => {
  const percent = percentValue(value);
  if (percent >= 85) {
    return '#dc2626';
  }
  if (percent >= 65) {
    return '#f59e0b';
  }
  return '#16a34a';
};

const RESOURCE_LEVELS = [
  { min: 85, label: '压力较高' },
  { min: 65, label: '需要关注' },
  { min: 0, label: '运行平稳' },
] as const;

const resourceLevelLabel = (value?: number | null) => {
  const percent = percentValue(value);
  return RESOURCE_LEVELS.find((item) => percent >= item.min)?.label || RESOURCE_LEVELS[RESOURCE_LEVELS.length - 1].label;
};

const maxResourceUsage = (snapshot?: HostResourceSnapshot) => Math.max(
  percentValue(snapshot?.cpuUsagePercent),
  percentValue(snapshot?.memoryUsagePercent),
  percentValue(snapshot?.diskUsagePercent),
);

function groupRuntimeEnvironments(items: RuntimeEnvironmentSummary[]) {
  const grouped = items.reduce<Record<string, RuntimeEnvironmentSummary[]>>((groups, item) => {
    const key = item.type || 'OTHER';
    groups[key] = [...(groups[key] || []), item];
    return groups;
  }, {});
  return sortRuntimeEnvironmentTypes(Object.keys(grouped))
    .filter((type) => grouped[type]?.length)
    .map((type) => ({ type, items: grouped[type] }));
}

export default function HostResourcePage() {
  const { hostId } = useParams();
  const navigate = useNavigate();
  const [snapshot, setSnapshot] = useState<HostResourceSnapshot>();
  const [environments, setEnvironments] = useState<RuntimeEnvironmentSummary[]>([]);
  const [loading, setLoading] = useState(true);

  const loadResources = async () => {
    if (!hostId) {
      return;
    }
    setLoading(true);
    try {
      const [resourceResult, environmentResult] = await Promise.all([
        hostsApi.previewResources(Number(hostId)),
        runtimeEnvironmentsApi.list(),
      ]);
      setSnapshot(resourceResult);
      setEnvironments(environmentResult.filter((item) => item.host?.id === Number(hostId)));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadResources().catch((error) => message.error(error?.response?.data?.message || '读取主机资源失败'));
  }, [hostId]);

  const environmentGroups = useMemo(() => groupRuntimeEnvironments(environments), [environments]);
  const overallUsage = maxResourceUsage(snapshot);

  return (
    <>
      <PageHeaderBar
        title={snapshot ? `${snapshot.hostName} · 资源看板` : '主机资源看板'}
        description="查看主机当前资源占用、工作空间磁盘和已配置运行环境。"
        extra={(
          <Space>
            <Button onClick={() => navigate('/admin/hosts')}>返回主机</Button>
            <Button type="primary" loading={loading} onClick={() => loadResources().catch((error) => message.error(error?.response?.data?.message || '刷新主机资源失败'))}>刷新</Button>
          </Space>
        )}
      />
      <div className="app-page-scroll">
        <div className="mx-auto max-w-6xl space-y-4">
          <Card className="app-card host-resource-hero" loading={loading}>
            {snapshot ? (
              <>
                <div className="host-resource-hero__main">
                  <div>
                    <div className="host-resource-hero__eyebrow">主机资源快照</div>
                    <div className="host-resource-hero__title">{snapshot.hostName}</div>
                    <div className="host-resource-hero__meta">
                      <span>{snapshot.osType || '未知系统'}</span>
                      <span>{snapshot.workspaceRoot || '-'}</span>
                      <span>{formatDateTime(snapshot.collectedAt)}</span>
                    </div>
                  </div>
                  <div className="host-resource-hero__load">
                    <Gauge weight="fill" />
                    <span>1 分钟负载</span>
                    <strong>{snapshot.loadAverage ?? '-'}</strong>
                  </div>
                </div>
                <div className="host-resource-status-strip">
                  <div className="host-resource-status-strip__item">
                    <Pulse weight="fill" />
                    <span>整体状态</span>
                    <strong>{resourceLevelLabel(overallUsage)}</strong>
                  </div>
                  <div className="host-resource-status-strip__item">
                    <TreeStructure weight="fill" />
                    <span>工作空间</span>
                    <strong>{snapshot.workspaceRoot || '-'}</strong>
                  </div>
                </div>
                <div className="host-resource-meter-grid">
                  <div className="host-resource-meter">
                    <Progress type="dashboard" percent={percentValue(snapshot.cpuUsagePercent)} strokeColor={resourceTone(snapshot.cpuUsagePercent)} />
                    <span>CPU 使用率</span>
                  </div>
                  <div className="host-resource-meter">
                    <Progress type="dashboard" percent={percentValue(snapshot.memoryUsagePercent)} strokeColor={resourceTone(snapshot.memoryUsagePercent)} />
                    <span>内存使用率</span>
                  </div>
                  <div className="host-resource-meter">
                    <Progress type="dashboard" percent={percentValue(snapshot.diskUsagePercent)} strokeColor={resourceTone(snapshot.diskUsagePercent)} />
                    <span>磁盘使用率</span>
                  </div>
                </div>
              </>
            ) : null}
          </Card>

          <div className="host-resource-stat-grid">
            <Card className="app-card host-resource-stat" loading={loading}>
              <Statistic title="CPU 核数" value={snapshot?.cpuCores ?? '-'} prefix={<Cpu weight="fill" />} />
            </Card>
            <Card className="app-card host-resource-stat" loading={loading}>
              <Statistic title="内存" value={snapshot ? `${snapshot.memoryUsedMb ?? '-'} / ${snapshot.memoryTotalMb ?? '-'} MB` : '-'} prefix={<Memory weight="fill" />} />
            </Card>
            <Card className="app-card host-resource-stat" loading={loading}>
              <Statistic title="磁盘" value={snapshot ? `${snapshot.diskUsedGb ?? '-'} / ${snapshot.diskTotalGb ?? '-'} GB` : '-'} prefix={<HardDrive weight="fill" />} />
            </Card>
            <Card className="app-card host-resource-stat" loading={loading}>
              <Statistic title="运行环境" value={`${environments.length} 个`} prefix={<Database weight="fill" />} />
            </Card>
          </div>

          <Card className="app-card" title="运行环境">
            {environmentGroups.length ? (
              <div className="host-resource-env-grid">
                {environmentGroups.map((group) => (
                  <div key={group.type} className="host-resource-env-group">
                    <div className="host-resource-env-group__title">
                      {getRuntimeEnvironmentTypeLabel(group.type)}
                      <span>{group.items.length} 个版本</span>
                    </div>
                    <div className="host-resource-env-group__items">
                      {group.items.map((item) => (
                        <span key={item.id}>
                          <strong>{item.name}</strong>
                          {item.version ? <em>{item.version}</em> : null}
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyPane description="这台主机还没有配置运行环境。" />
            )}
          </Card>
        </div>
      </div>
    </>
  );
}
