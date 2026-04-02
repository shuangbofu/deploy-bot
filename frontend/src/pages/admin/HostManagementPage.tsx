import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Descriptions, Form, Input, Modal, Popconfirm, Progress, Select, Space, Switch, Table, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { hostsApi } from '../../api/hosts';
import { runtimeEnvironmentsApi } from '../../api/runtimeEnvironments';
import type { HostPayload } from '../../api/types';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import type {
  HostConnectionTestResult,
  HostResourceSnapshot,
  HostSshAuthType,
  HostSummary,
  HostType,
  RuntimeEnvironmentSummary,
} from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';
import { getStableTagColor } from '../../utils/tagColors';

const hostTypeOptions: { label: string; value: HostType }[] = [
  { label: '本机', value: 'LOCAL' },
  { label: 'SSH 远程主机', value: 'SSH' },
];

const sshAuthTypeOptions: { label: string; value: HostSshAuthType }[] = [
  { label: '密码', value: 'PASSWORD' },
  { label: '私钥', value: 'PRIVATE_KEY' },
  { label: '系统密钥对', value: 'SYSTEM_KEY_PAIR' },
];

interface HostFormState {
  name: string;
  type: HostType;
  description: string;
  hostname: string;
  port: number;
  username: string;
  sshAuthType: HostSshAuthType;
  sshPassword: string;
  sshPrivateKey: string;
  sshPassphrase: string;
  workspaceRoot: string;
  sshKnownHosts: string;
  enabled: boolean;
}

const emptyHost: HostFormState = {
  name: '',
  type: 'LOCAL',
  description: '',
  hostname: '',
  port: 22,
  username: '',
  sshAuthType: 'SYSTEM_KEY_PAIR',
  sshPassword: '',
  sshPrivateKey: '',
  sshPassphrase: '',
  workspaceRoot: '',
  sshKnownHosts: '',
  enabled: true,
};

export default function HostManagementPage() {
  const navigate = useNavigate();
  const [hosts, setHosts] = useState<HostSummary[]>([]);
  const [environments, setEnvironments] = useState<RuntimeEnvironmentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<HostFormState>(emptyHost);
  const [editingId, setEditingId] = useState<number | undefined>();
  const [modalOpen, setModalOpen] = useState(false);
  const [testingHostId, setTestingHostId] = useState<number | undefined>();
  const [previewingHostId, setPreviewingHostId] = useState<number | undefined>();
  const [resourceModalOpen, setResourceModalOpen] = useState(false);
  const [resourceSnapshot, setResourceSnapshot] = useState<HostResourceSnapshot>();
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<HostType>();
  const [enabledFilter, setEnabledFilter] = useState<string>();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  const loadHosts = async () => {
    setLoading(true);
    try {
      const [hostResponse, environmentResponse] = await Promise.all([
        hostsApi.list(),
        runtimeEnvironmentsApi.list(),
      ]);
      setHosts(hostResponse);
      setEnvironments(environmentResponse);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadHosts().catch(() => message.error('加载主机失败'));
  }, []);

  const openCreate = () => {
    setEditingId(undefined);
    setForm(emptyHost);
    setModalOpen(true);
  };

  const openEdit = (record: HostSummary) => {
    setEditingId(record.id);
    setForm({
      name: record.name || '',
      type: record.type || 'LOCAL',
      description: record.description || '',
      hostname: record.hostname || '',
      port: record.port || 22,
      username: record.username || '',
      sshAuthType: record.sshAuthType || 'SYSTEM_KEY_PAIR',
      sshPassword: record.sshPassword || '',
      sshPrivateKey: record.sshPrivateKey || '',
      sshPassphrase: record.sshPassphrase || '',
      workspaceRoot: record.workspaceRoot || '',
      sshKnownHosts: record.sshKnownHosts || '',
      enabled: record.enabled !== false,
    });
    setModalOpen(true);
  };

  const saveHost = async () => {
    const payload: HostPayload = form;
    if (editingId) {
      await hostsApi.update(editingId, payload);
    } else {
      await hostsApi.create(payload);
    }
    setModalOpen(false);
    setEditingId(undefined);
    setForm(emptyHost);
    await loadHosts();
    message.success(editingId ? '主机已更新' : '主机已创建');
  };

  const removeHost = async (id: number) => {
    await hostsApi.remove(id);
    await loadHosts();
    message.success('主机已删除');
  };

  const testConnection = async (id: number) => {
    setTestingHostId(id);
    try {
      const result = await hostsApi.testConnection(id);
      if (result.success) {
        Modal.success({
          title: '连接成功',
          content: (
            <div className="space-y-2 text-sm text-slate-700">
              <div>说明：{result.message}</div>
              <div>远程用户：{result.remoteUser || '-'}</div>
              <div>远程主机：{result.remoteHost || '-'}</div>
              <div>工作空间：{result.workspaceRoot || '-'}</div>
            </div>
          ),
        });
      } else {
        Modal.error({
          title: '连接失败',
          content: (
            <div className="space-y-2 text-sm text-slate-700">
              <div>{result.message || '连接失败'}</div>
            </div>
          ),
        });
      }
    } finally {
      setTestingHostId(undefined);
    }
  };

  const previewResources = async (id: number) => {
    setPreviewingHostId(id);
    try {
      setResourceSnapshot(await hostsApi.previewResources(id));
      setResourceModalOpen(true);
    } finally {
      setPreviewingHostId(undefined);
    }
  };

  const filteredHosts = useMemo(() => hosts.filter((item) => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    if (normalizedKeyword) {
      const matched = [item.name, item.description, item.hostname, item.workspaceRoot]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedKeyword));
      if (!matched) {
        return false;
      }
    }
    if (typeFilter && item.type !== typeFilter) {
      return false;
    }
    if (enabledFilter != null) {
      const expected = enabledFilter === 'true';
      if (Boolean(item.enabled !== false) !== expected) {
        return false;
      }
    }
    return true;
  }), [hosts, keyword, typeFilter, enabledFilter]);

  return (
    <>
      <PageHeaderBar
        title="主机管理"
        description="管理本机和远程主机，以及对应的连接和工作空间配置。"
        extra={(
          <Space>
            <Button onClick={() => loadHosts().catch(() => message.error('加载主机失败'))}>刷新</Button>
            <Button type="primary" onClick={openCreate}>新建主机</Button>
          </Space>
        )}
      />
      <Card className="app-card">
        <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-4">
          <Input
            value={keyword}
            placeholder="搜索主机名称 / 地址 / 工作空间"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={typeFilter}
            placeholder="筛选主机类型"
            options={hostTypeOptions}
            onChange={(value) => {
              setTypeFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={enabledFilter}
            placeholder="筛选启用状态"
            options={[
              { label: '启用', value: 'true' },
              { label: '停用', value: 'false' },
            ]}
            onChange={(value) => {
              setEnabledFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <div className="flex items-center">
            <Button
              onClick={() => {
                setKeyword('');
                setTypeFilter(undefined);
                setEnabledFilter(undefined);
                setPagination((previous) => ({ ...previous, current: 1 }));
              }}
            >
              重置条件
            </Button>
          </div>
        </div>
        <Table
          rowKey="id"
          loading={loading}
          scroll={{ x: 1080 }}
          dataSource={filteredHosts}
          locale={{ emptyText: <EmptyPane description="还没有主机，系统会自动内置一条本机记录。" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: filteredHosts.length,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (current, pageSize) => setPagination({ current, pageSize }),
          }}
          columns={[
              { title: '名称', dataIndex: 'name', width: 180 },
              { title: '类型', render: (_, row) => row.type === 'LOCAL' ? '本机' : 'SSH 远程主机', width: 140 },
              { title: '说明', dataIndex: 'description' },
              { title: '主机地址', render: (_, row) => row.type === 'LOCAL' ? '-' : row.hostname || '-', width: 180 },
              { title: '端口', render: (_, row) => row.type === 'LOCAL' ? '-' : (row.port || 22), width: 100 },
              { title: '用户名', render: (_, row) => row.type === 'LOCAL' ? '-' : row.username || '-', width: 120 },
              { title: '认证方式', render: (_, row) => row.type === 'LOCAL' ? '-' : (row.sshAuthType === 'PASSWORD' ? '密码' : row.sshAuthType === 'PRIVATE_KEY' ? '私钥' : '系统密钥对'), width: 120 },
              { title: '工作空间', dataIndex: 'workspaceRoot' },
              { title: '主机指纹', render: (_, row) => row.type === 'LOCAL' ? '-' : (row.sshKnownHosts ? '已配置' : '未配置'), width: 100 },
              {
                title: '运行环境',
                render: (_, row) => {
                  const items = environments.filter((item) => item.host?.id === row.id);
                  if (items.length === 0) {
                    return '-';
                  }
                  return items.map((item) => item.name).join(' / ');
                },
              },
              { title: '状态', render: (_, row) => row.enabled !== false ? '启用' : '停用', width: 100 },
              {
                title: '内置',
                width: 110,
                render: (_, row) => row.builtIn ? (
                  <span
                    className="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold text-white"
                    style={{ backgroundColor: getStableTagColor('系统内置') }}
                  >
                    系统内置
                  </span>
                ) : '-',
              },
              {
                title: '操作',
                width: 420,
                render: (_, record) => (
                  <Space>
                    <Button size="small" onClick={() => navigate(`/admin/hosts/${record.id}/environments`)}>
                      环境管理
                    </Button>
                    <Button size="small" loading={testingHostId === record.id} onClick={() => testConnection(record.id).catch((error) => message.error(error?.response?.data?.message || '测试连接失败'))}>
                      测试连接
                    </Button>
                    <Button size="small" loading={previewingHostId === record.id} onClick={() => previewResources(record.id).catch((error) => message.error(error?.response?.data?.message || '读取主机资源失败'))}>
                      资源预览
                    </Button>
                    <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
                    <Popconfirm
                      title="确认删除这台主机吗？"
                      okText="确认"
                      cancelText="取消"
                      disabled={Boolean(record.builtIn)}
                      onConfirm={() => removeHost(record.id).catch((error) => message.error(error?.response?.data?.message || '删除主机失败'))}
                    >
                      <Button size="small" danger disabled={Boolean(record.builtIn)}>删除</Button>
                    </Popconfirm>
                  </Space>
                ),
              },
          ]}
        />
      </Card>
      <Modal
        title={editingId ? '编辑主机' : '新建主机'}
        open={modalOpen}
        width={720}
        okText="保存"
        cancelText="取消"
        onCancel={() => setModalOpen(false)}
        onOk={() => saveHost().catch((error) => message.error(error?.response?.data?.message || (editingId ? '更新主机失败' : '创建主机失败')))}
      >
        <Form layout="vertical">
          <Form.Item label="主机名称">
            <Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
          </Form.Item>
          <Form.Item label="主机类型">
            <Select value={form.type} options={hostTypeOptions} onChange={(value) => setForm({ ...form, type: value })} disabled={Boolean(hosts.find((item) => item.id === editingId)?.builtIn)} />
          </Form.Item>
          <Form.Item label="说明">
            <Input.TextArea rows={3} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
          </Form.Item>
          {form.type === 'SSH' ? (
            <>
              <Form.Item label="主机地址">
                <Input value={form.hostname} onChange={(event) => setForm({ ...form, hostname: event.target.value })} placeholder="例如：192.168.1.21" />
              </Form.Item>
              <Form.Item label="SSH 端口">
                <Input type="number" value={form.port} onChange={(event) => setForm({ ...form, port: Number(event.target.value || 22) })} />
              </Form.Item>
              <Form.Item label="SSH 用户名">
                <Input value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} placeholder="例如：deploy" />
              </Form.Item>
              <Form.Item label="SSH 认证方式">
                <Select
                  value={form.sshAuthType}
                  options={sshAuthTypeOptions}
                  onChange={(value: HostSshAuthType) => setForm({ ...form, sshAuthType: value, sshPassword: '', sshPrivateKey: '', sshPassphrase: '' })}
                />
              </Form.Item>
              {form.sshAuthType === 'PASSWORD' ? (
                <Form.Item label="SSH 密码">
                  <Input.Password value={form.sshPassword} onChange={(event) => setForm({ ...form, sshPassword: event.target.value })} placeholder="请输入远程主机登录密码" />
                </Form.Item>
              ) : null}
              {form.sshAuthType === 'PRIVATE_KEY' ? (
                <>
                  <Form.Item label="SSH 私钥">
                    <Input.TextArea rows={6} value={form.sshPrivateKey} onChange={(event) => setForm({ ...form, sshPrivateKey: event.target.value })} placeholder="支持 OpenSSH 私钥或 PEM 私钥内容" />
                  </Form.Item>
                  <Form.Item label="私钥口令">
                    <Input.Password value={form.sshPassphrase} onChange={(event) => setForm({ ...form, sshPassphrase: event.target.value })} placeholder="可选。如果私钥有 passphrase，可先保存；当前执行器暂不自动解锁带口令私钥。" />
                  </Form.Item>
                </>
              ) : null}
              {form.sshAuthType === 'SYSTEM_KEY_PAIR' ? (
                <div className="mb-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
                  当前主机会使用系统设置里的“主机 SSH 密钥对”登录。请先去系统设置生成主机密钥对，再把公钥配置到目标主机的 `authorized_keys`。
                </div>
              ) : null}
              <Form.Item label="主机指纹 known_hosts">
                <Input.TextArea
                  rows={4}
                  value={form.sshKnownHosts}
                  onChange={(event) => setForm({ ...form, sshKnownHosts: event.target.value })}
                  placeholder="可选。填写后会校验这台主机的 SSH 指纹；不填则默认关闭 StrictHostKeyChecking。"
                />
              </Form.Item>
            </>
          ) : null}
          <Form.Item label="工作空间根目录">
            <Input
              value={form.workspaceRoot}
              onChange={(event) => setForm({ ...form, workspaceRoot: event.target.value })}
              placeholder={form.type === 'LOCAL' ? './runtime' : '/opt/deploy-bot'}
            />
          </Form.Item>
          <Form.Item label="是否启用">
            <Switch checked={form.enabled} checkedChildren="启用" unCheckedChildren="停用" onChange={(checked) => setForm({ ...form, enabled: checked })} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={resourceSnapshot ? `${resourceSnapshot.hostName} · 资源预览` : '资源预览'}
        open={resourceModalOpen}
        width={760}
        footer={null}
        onCancel={() => setResourceModalOpen(false)}
      >
        {resourceSnapshot ? (
          <div className="space-y-4">
            <Descriptions column={2} size="small">
              <Descriptions.Item label="采集时间">{formatDateTime(resourceSnapshot.collectedAt)}</Descriptions.Item>
              <Descriptions.Item label="系统类型">{resourceSnapshot.osType || '-'}</Descriptions.Item>
              <Descriptions.Item label="工作空间">{resourceSnapshot.workspaceRoot || '-'}</Descriptions.Item>
              <Descriptions.Item label="CPU 核数">{resourceSnapshot.cpuCores ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="CPU 使用率">{resourceSnapshot.cpuUsagePercent ?? '-'}%</Descriptions.Item>
              <Descriptions.Item label="1 分钟负载">{resourceSnapshot.loadAverage ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="内存">{resourceSnapshot.memoryUsedMb ?? '-'} MB / {resourceSnapshot.memoryTotalMb ?? '-'} MB</Descriptions.Item>
              <Descriptions.Item label="磁盘">{resourceSnapshot.diskUsedGb ?? '-'} GB / {resourceSnapshot.diskTotalGb ?? '-'} GB</Descriptions.Item>
            </Descriptions>
            <div>
              <div className="mb-2 text-sm font-medium text-slate-700">CPU 使用率</div>
              <Progress percent={resourceSnapshot.cpuUsagePercent ?? 0} strokeColor="#f59e0b" />
            </div>
            <div>
              <div className="mb-2 text-sm font-medium text-slate-700">内存使用率</div>
              <Progress percent={resourceSnapshot.memoryUsagePercent ?? 0} strokeColor="#2563eb" />
            </div>
            <div>
              <div className="mb-2 text-sm font-medium text-slate-700">磁盘使用率</div>
              <Progress percent={resourceSnapshot.diskUsagePercent ?? 0} strokeColor="#16a34a" />
            </div>
            <Card size="small" title="预览文本">
              <pre className="table-code-preview">{resourceSnapshot.preview || '暂无资源预览文本。'}</pre>
            </Card>
          </div>
        ) : null}
      </Modal>
    </>
  );
}
