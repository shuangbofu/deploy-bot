import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Segmented, Select, Space, Switch, Table, Tag, message } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { hostsApi } from '../../api/hosts';
import { runtimeEnvironmentsApi } from '../../api/runtimeEnvironments';
import type { DetectionItem, PresetItem, RuntimeEnvironmentInstallTaskStatus, RuntimeEnvironmentPayload } from '../../api/types';
import BooleanBadge from '../../components/BooleanBadge';
import EnvironmentVariablesEditor from '../../components/EnvironmentVariablesEditor';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import type { HostSummary, RuntimeEnvironmentSummary, RuntimeEnvironmentType } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';

const typeOptions: { label: string; value: RuntimeEnvironmentType }[] = [
  { label: 'Java', value: 'JAVA' },
  { label: 'Node', value: 'NODE' },
  { label: 'Maven', value: 'MAVEN' },
];

const usageOptions = [
  { label: '全部用途', value: 'all' },
  { label: '构建', value: 'build' },
  { label: '部署', value: 'deploy' },
];

const presetTypeOptions = [
  { label: '全部组件', value: 'all' },
  { label: 'Java', value: 'JAVA' },
  { label: 'Node', value: 'NODE' },
  { label: 'Maven', value: 'MAVEN' },
];

interface EnvironmentVariableItem {
  key: string;
  value: string;
  prependPath: boolean;
}

interface RuntimeEnvironmentFormState {
  name: string;
  type: RuntimeEnvironmentType;
  hostId?: number;
  version: string;
  homePath: string;
  binPath: string;
  activationScript: string;
  environmentVariables: EnvironmentVariableItem[];
  enabled: boolean;
}

const emptyEnvironment: RuntimeEnvironmentFormState = {
  name: '',
  type: 'JAVA',
  hostId: undefined,
  version: '',
  homePath: '',
  binPath: '',
  activationScript: '',
  environmentVariables: [],
  enabled: true,
};

const parseEnvironmentVariables = (content: unknown): EnvironmentVariableItem[] => {
  if (!content) {
    return [];
  }
  if (Array.isArray(content)) {
    return content;
  }
  try {
    const parsed = JSON.parse(content);
    if (!parsed || typeof parsed !== 'object') {
      return [];
    }
    return Object.entries(parsed).map(([key, value]) => {
      if (typeof value === 'string') {
        return { key, value, prependPath: false };
      }
      const typedValue = value as { value?: string; prependPath?: boolean };
      return {
        key,
        value: typedValue?.value || '',
        prependPath: Boolean(typedValue?.prependPath),
      };
    });
  } catch {
    return [];
  }
};

const stringifyEnvironmentVariables = (items: EnvironmentVariableItem[]) => JSON.stringify(
  items
    .filter((item) => item.key && item.value)
    .reduce((result, item) => {
      result[item.key.trim()] = {
        value: item.value,
        prependPath: Boolean(item.prependPath),
      };
      return result;
    }, {}),
  null,
  2,
);

const getRequestErrorMessage = (error: unknown, fallback: string) => {
  const typedError = error as { response?: { data?: { message?: string } }; message?: string };
  const responseMessage = typedError?.response?.data?.message;
  if (responseMessage) {
    return responseMessage;
  }
  return typedError?.message || fallback;
};

const isValidDetection = (item: DetectionItem) => {
  const version = String(item?.version || '').trim().toLowerCase();
  if (!version) {
    return false;
  }
  return !version.includes('command not found')
    && !version.includes('not recognized')
    && !version.includes('no such file or directory')
    && !version.includes('cannot find');
};

export default function RuntimeEnvironmentsPage() {
  const navigate = useNavigate();
  const { hostId } = useParams();
  const [hosts, setHosts] = useState<HostSummary[]>([]);
  const [environments, setEnvironments] = useState<RuntimeEnvironmentSummary[]>([]);
  const [form, setForm] = useState<RuntimeEnvironmentFormState>(emptyEnvironment);
  const [editingId, setEditingId] = useState<number>();
  const [modalOpen, setModalOpen] = useState(false);
  const [detecting, setDetecting] = useState(false);
  const [detections, setDetections] = useState<DetectionItem[]>([]);
  const [detectModalOpen, setDetectModalOpen] = useState(false);
  const [presets, setPresets] = useState<PresetItem[]>([]);
  const [presetModalOpen, setPresetModalOpen] = useState(false);
  const [installingPresetId, setInstallingPresetId] = useState<string>();
  const [installTasks, setInstallTasks] = useState<RuntimeEnvironmentInstallTaskStatus[]>([]);
  const [presetUsageFilter, setPresetUsageFilter] = useState('all');
  const [presetTypeFilter, setPresetTypeFilter] = useState('all');

  const loadEnvironments = async () => {
    const [hostResponse, response, taskResponse] = await Promise.all([
      hostsApi.list(),
      runtimeEnvironmentsApi.list(hostId),
      runtimeEnvironmentsApi.installTasks(hostId),
    ]);
    setHosts(hostResponse);
    setEnvironments(response);
    setInstallTasks(taskResponse);
  };

  useEffect(() => {
    loadEnvironments().catch(() => message.error('加载运行环境失败'));
  }, [hostId]);

  const activeInstallTasks = useMemo(
    () => installTasks.filter((item) => ['PENDING', 'RUNNING'].includes(item.status)),
    [installTasks],
  );

  useEffect(() => {
    if (activeInstallTasks.length === 0) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      loadEnvironments().catch(() => undefined);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [activeInstallTasks.length, hostId]);

  const currentHost = useMemo(
    () => hosts.find((item) => String(item.id) === String(hostId)),
    [hosts, hostId],
  );

  const groupedData = useMemo(() => environments.map((item) => ({
    ...item,
    typeLabel: typeOptions.find((option) => option.value === item.type)?.label || item.type,
  })), [environments]);

  const openCreate = () => {
    const defaultHostId = currentHost?.id || hosts.find((item) => item.builtIn)?.id || hosts[0]?.id;
    setEditingId(undefined);
    setForm({ ...emptyEnvironment, hostId: defaultHostId });
    setModalOpen(true);
  };

  const openEdit = (record) => {
    setEditingId(record.id);
    setForm({
      name: record.name || '',
      type: record.type || 'JAVA',
      hostId: record.host?.id || currentHost?.id,
      version: record.version || '',
      homePath: record.homePath || '',
      binPath: record.binPath || '',
      activationScript: record.activationScript || '',
      environmentVariables: parseEnvironmentVariables(record.environmentJson),
      enabled: record.enabled !== false,
    });
    setModalOpen(true);
  };

  const saveEnvironment = async () => {
    const payload: RuntimeEnvironmentPayload = {
      ...form,
      environmentJson: stringifyEnvironmentVariables(form.environmentVariables),
    };
    if (editingId) {
      await runtimeEnvironmentsApi.update(editingId, payload);
    } else {
      await runtimeEnvironmentsApi.create(payload);
    }
    setForm(emptyEnvironment);
    setEditingId(undefined);
    setModalOpen(false);
    await loadEnvironments();
    message.success(editingId ? '运行环境已更新' : '运行环境已创建');
  };

  const removeEnvironment = async (id: number) => {
    await runtimeEnvironmentsApi.remove(id);
    await loadEnvironments();
    message.success('运行环境已删除');
  };

  const openDetectModal = async () => {
    setDetecting(true);
    setDetectModalOpen(true);
    try {
      setDetections(await runtimeEnvironmentsApi.detections(hostId));
    } finally {
      setDetecting(false);
    }
  };

  const importDetection = async (item: DetectionItem) => {
    await runtimeEnvironmentsApi.create({
      name: item.name,
      type: item.type,
      hostId: currentHost?.id || hosts.find((host) => host.builtIn)?.id,
      version: item.version,
      homePath: item.homePath,
      binPath: item.binPath,
      activationScript: '',
      environmentJson: '{}',
      enabled: true,
    });
    await loadEnvironments();
    message.success('自动检测结果已导入');
  };

  const openPresetModal = async () => {
    setPresets(await runtimeEnvironmentsApi.presets(hostId));
    setPresetUsageFilter('all');
    setPresetTypeFilter('all');
    setPresetModalOpen(true);
  };

  const installPreset = async (presetId: string) => {
    setInstallingPresetId(presetId);
    try {
      const result = await runtimeEnvironmentsApi.installPreset({
        presetId,
        hostId: currentHost?.id || hosts.find((item) => item.builtIn)?.id,
      });
      setPresetModalOpen(false);
      await loadEnvironments();
      message.success(result.message || '预置环境已开始后台下载安装');
    } finally {
      setInstallingPresetId(undefined);
    }
  };

  const renderInstallTaskStatus = (item: RuntimeEnvironmentInstallTaskStatus) => {
    if (item.status === 'SUCCESS') {
      return <Tag color="success">安装完成</Tag>;
    }
    if (item.status === 'FAILED') {
      return <Tag color="error">安装失败</Tag>;
    }
    if (item.status === 'RUNNING') {
      return <Tag color="processing">安装中</Tag>;
    }
    return <Tag color="default">等待中</Tag>;
  };

  const getPresetUsages = (preset) => {
    if (preset.type === 'NODE') {
      return ['build', 'deploy'];
    }
    if (preset.type === 'JAVA') {
      return ['build', 'deploy'];
    }
    if (preset.type === 'MAVEN') {
      return ['build'];
    }
    return ['build'];
  };

  const filteredPresets = useMemo(
    () => presets.filter((item) => {
      const matchesUsage = presetUsageFilter === 'all' || getPresetUsages(item).includes(presetUsageFilter);
      const matchesType = presetTypeFilter === 'all' || item.type === presetTypeFilter;
      return matchesUsage && matchesType;
    }),
    [presets, presetTypeFilter, presetUsageFilter],
  );

  return (
    <>
      <PageHeaderBar
        title={currentHost ? `${currentHost.name} 的运行环境` : '运行环境'}
        description={currentHost
          ? '这里维护当前主机下的 Java、Node、Maven 环境。流水线选中目标主机后，只能选择这台主机下的环境。'
          : '统一维护 Java、Node、Maven 的版本和路径。'}
        extra={[
          currentHost ? <Button key="back" onClick={() => navigate('/admin/hosts')}>返回主机管理</Button> : null,
          <Button key="refresh" onClick={() => loadEnvironments().catch(() => message.error('刷新运行环境失败'))}>刷新</Button>,
          <Button key="detect" onClick={() => openDetectModal().catch(() => message.error('自动检测失败'))}>自动检测</Button>,
          <Button key="preset" onClick={() => openPresetModal().catch(() => message.error('加载预置失败'))}>下载预置</Button>,
          <Button key="create" type="primary" onClick={openCreate}>新建运行环境</Button>,
        ]}
      />
      <Card className="app-card mb-4" size="small" title="安装任务">
        {installTasks.length === 0 ? (
          <EmptyPane description="当前还没有下载安装记录。" />
        ) : (
          <div className="space-y-3">
            {installTasks.slice(0, 8).map((item) => (
              <div key={item.taskId} className="flex items-start justify-between gap-4 rounded-xl border border-slate-200 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <div className="font-medium text-slate-800">{item.presetName}</div>
                    {renderInstallTaskStatus(item)}
                  </div>
                  <div className="mt-1 text-sm text-slate-500">
                    {item.message || '-'}
                  </div>
                  <div className="mt-2 text-xs text-slate-400">
                    开始时间：{formatDateTime(item.startedAt)}{item.finishedAt ? ` · 结束时间：${formatDateTime(item.finishedAt)}` : ''}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
      <Card className="app-card">
        {groupedData.length === 0 ? (
          <EmptyPane description="还没有运行环境，先录入 Java / Node / Maven 的可用版本。" />
        ) : (
          <Table
            rowKey="id"
            scroll={{ x: 1120 }}
            dataSource={groupedData}
            columns={[
              { title: '名称', dataIndex: 'name', width: 220 },
              { title: '主机', render: (_, record) => record.host?.name || '-', width: 140 },
              { title: '类型', dataIndex: 'typeLabel', width: 120 },
              { title: '版本', dataIndex: 'version', width: 140 },
              { title: 'Home 路径', dataIndex: 'homePath' },
              { title: 'Bin 路径', dataIndex: 'binPath' },
              {
                title: '激活命令',
                render: (_, record) => record.activationScript ? <pre className="table-code-preview">{record.activationScript}</pre> : <span className="text-slate-400">无</span>,
              },
              {
                title: '附加环境变量',
                render: (_, record) => record.environmentJson && record.environmentJson !== '{}' ? <pre className="table-code-preview">{record.environmentJson}</pre> : <span className="text-slate-400">无</span>,
              },
              {
                title: '启用',
                width: 100,
                render: (_, record) => <BooleanBadge value={Boolean(record.enabled)} trueLabel="启用" falseLabel="停用" />,
              },
              {
                title: '操作',
                width: 180,
                render: (_, record) => (
                  <Space>
                    <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
                    <Popconfirm
                      title="确认删除这个运行环境吗？"
                      okText="确认"
                      cancelText="取消"
                      onConfirm={() => removeEnvironment(record.id).catch(() => message.error('删除运行环境失败'))}
                    >
                      <Button size="small" danger>删除</Button>
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        )}
      </Card>
      <Modal
        title={editingId ? '编辑运行环境' : '新建运行环境'}
        open={modalOpen}
        width={760}
        okText="保存"
        cancelText="取消"
        onCancel={() => setModalOpen(false)}
        onOk={() => saveEnvironment().catch(() => message.error(editingId ? '更新运行环境失败' : '创建运行环境失败'))}
        destroyOnClose
      >
        <Form layout="vertical">
          <Form.Item label="环境名称">
            <Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="例如：Java 17、Node 18、Maven 3.9" />
          </Form.Item>
          <Form.Item label="归属主机">
            <Select
              value={form.hostId}
              options={hosts.filter((item) => item.enabled !== false).map((item) => ({ label: item.name, value: item.id }))}
              onChange={(value) => setForm({ ...form, hostId: value })}
              placeholder="请选择主机"
            />
          </Form.Item>
          <Form.Item label="环境类型">
            <Select value={form.type} options={typeOptions} onChange={(value) => setForm({ ...form, type: value })} />
          </Form.Item>
          <Form.Item label="版本">
            <Input value={form.version} onChange={(event) => setForm({ ...form, version: event.target.value })} placeholder="例如：17.0.10 / 18.19.1 / 3.9.8" />
          </Form.Item>
          <Form.Item label="Home 路径">
            <Input value={form.homePath} onChange={(event) => setForm({ ...form, homePath: event.target.value })} placeholder="/opt/java/jdk-17" />
          </Form.Item>
          <Form.Item label="Bin 路径">
            <Input value={form.binPath} onChange={(event) => setForm({ ...form, binPath: event.target.value })} placeholder="/opt/java/jdk-17/bin" />
          </Form.Item>
          <Form.Item label="激活命令">
            <Input.TextArea
              rows={4}
              value={form.activationScript}
              onChange={(event) => setForm({ ...form, activationScript: event.target.value })}
              placeholder={form.type === 'NODE'
                ? '适用于 nvm，例如：\nexport NVM_DIR=\"$HOME/.nvm\"\n[ -s \"$NVM_DIR/nvm.sh\" ] && . \"$NVM_DIR/nvm.sh\"\nnvm use 16'
                : '可选。适用于需要先执行初始化命令的环境。'}
            />
          </Form.Item>
          <Form.Item label="附加环境变量">
            <EnvironmentVariablesEditor
              value={form.environmentVariables}
              onChange={(value) => setForm({ ...form, environmentVariables: value })}
            />
          </Form.Item>
          <Form.Item label="是否启用">
            <Switch checked={form.enabled} checkedChildren="启用" unCheckedChildren="停用" onChange={(checked) => setForm({ ...form, enabled: checked })} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="自动检测运行环境"
        open={detectModalOpen}
        footer={null}
        onCancel={() => setDetectModalOpen(false)}
      >
        <div className="space-y-3">
          {detecting ? <div className="text-slate-500">正在检测{currentHost ? `“${currentHost.name}”` : '本机'}已有的 Java / Node / Maven...</div> : null}
          {!detecting && detections.length === 0 ? <div className="text-slate-500">没有检测到可导入的环境。</div> : null}
          {!detecting ? <div className="text-slate-500">自动检测结果会直接导入到当前主机下。</div> : null}
          {detections.map((item) => (
            <Card key={`${item.type}-${item.name}`} size="small">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="font-medium text-slate-800">{item.name}</div>
                  <div className="mt-1 text-sm text-slate-500">{item.version || '未识别到版本号'}</div>
                  <div className="mt-2 text-xs text-slate-500">Home：{item.homePath || '-'}</div>
                  <div className="text-xs text-slate-500">Bin：{item.binPath || '-'}</div>
                  {!isValidDetection(item) ? (
                    <div className="mt-2 text-xs text-red-500">当前检测结果无效，不能导入。</div>
                  ) : null}
                </div>
                <Button
                  type="primary"
                  disabled={!isValidDetection(item)}
                  onClick={() => importDetection(item).catch(() => message.error('导入检测结果失败'))}
                >
                  导入
                </Button>
              </div>
            </Card>
          ))}
        </div>
      </Modal>
      <Modal
        title="下载常用运行环境"
        open={presetModalOpen}
        footer={null}
        width={860}
        onCancel={() => setPresetModalOpen(false)}
      >
        <div className="mb-4 text-slate-500">
          预置环境会安装到当前主机的工作空间下。远程主机会通过 SSH 下载、解压并注册到对应主机。
        </div>
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Segmented options={usageOptions} value={presetUsageFilter} onChange={setPresetUsageFilter} />
          <Segmented options={presetTypeOptions} value={presetTypeFilter} onChange={setPresetTypeFilter} />
        </div>
        {filteredPresets.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-200 px-4 py-8 text-center text-slate-500">
            当前筛选条件下没有可用预置。
          </div>
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {filteredPresets.map((item) => (
            <Card key={item.id} size="small">
              <div className="flex h-full flex-col justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2">
                    <div className="font-medium text-slate-800">{item.name}</div>
                    <Tag>{typeOptions.find((option) => option.value === item.type)?.label || item.type}</Tag>
                    {getPresetUsages(item).map((usage) => (
                      <Tag key={usage} color={usage === 'deploy' ? 'blue' : 'green'}>
                        {usage === 'deploy' ? '部署' : '构建'}
                      </Tag>
                    ))}
                  </div>
                  <div className="mt-1 text-sm text-slate-500">{item.description}</div>
                  <div className="mt-2 text-xs text-slate-500">版本：{item.version}</div>
                  <div className="text-xs text-slate-500 break-all">安装目录：{item.homePath}</div>
                </div>
                <Space>
                  <Button
                    type="primary"
                    loading={installingPresetId === item.id}
                    onClick={() => installPreset(item.id).catch((error) => message.error(getRequestErrorMessage(error, '安装预置环境失败')))}
                  >
                    下载并安装
                  </Button>
                </Space>
              </div>
            </Card>
            ))}
          </div>
        )}
      </Modal>
    </>
  );
}
