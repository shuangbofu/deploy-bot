import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Steps, Table, Tag, message } from 'antd';
import { hostsApi } from '../../api/hosts';
import { pipelinesApi } from '../../api/pipelines';
import { projectsApi } from '../../api/projects';
import { runtimeEnvironmentsApi } from '../../api/runtimeEnvironments';
import { templatesApi } from '../../api/templates';
import type { PipelinePayload } from '../../api/types';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import PipelineVariablesEditor from '../../components/PipelineVariablesEditor';
import PipelineIcon, { getRequiredEnvironmentTypes } from '../../components/PipelineIcon';
import type {
  HostSummary,
  PipelineSummary,
  RuntimeEnvironmentSummary,
  TemplateSummary,
  TemplateVariableDefinition,
} from '../../types/domain';

interface PipelineFormState {
  name: string;
  description: string;
  projectId?: number;
  templateId?: number;
  targetHostId?: number;
  defaultBranch: string;
  variablesJson: Record<string, string>;
  javaEnvironmentId?: number;
  nodeEnvironmentId?: number;
  mavenEnvironmentId?: number;
}

const emptyPipeline: PipelineFormState = {
  name: '',
  description: '',
  projectId: undefined,
  templateId: undefined,
  targetHostId: undefined,
  defaultBranch: 'main',
  variablesJson: {},
  javaEnvironmentId: undefined,
  nodeEnvironmentId: undefined,
  mavenEnvironmentId: undefined,
};

/**
 * 模板变量可能以字符串或数组返回，这里统一转成结构化数组。
 */
const parseVariablesSchema = (content: unknown): TemplateVariableDefinition[] => {
  if (!content) {
    return [];
  }
  if (Array.isArray(content)) {
    return content;
  }
  try {
    const parsed = JSON.parse(content);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

/**
 * 流水线默认变量在接口里可能是 JSON 字符串，也可能已经被前端处理成对象。
 */
const parseVariablesJson = (content: unknown): Record<string, string> => {
  if (!content) {
    return {};
  }
  if (typeof content === 'object') {
    return content;
  }
  try {
    const parsed = JSON.parse(content);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
};

const getLocalBuildEnvironmentOptions = (
  items: RuntimeEnvironmentSummary[],
  type: RuntimeEnvironmentSummary['type'],
  localHostId?: number,
) => {
  if (!localHostId) {
    return [];
  }

  const candidates = items.filter((item) => item?.type === type && item.enabled !== false && (item.host?.id === localHostId || item.host == null));
  const deduped = new Map();

  candidates.forEach((item) => {
    const key = [item.type, item.name || '', item.version || ''].join('::');
    const existing = deduped.get(key);
    if (!existing) {
      deduped.set(key, item);
      return;
    }
    const currentIsLocal = item.host?.id === localHostId;
    const existingIsLocal = existing.host?.id === localHostId;
    if (currentIsLocal && !existingIsLocal) {
      deduped.set(key, item);
    }
  });

  return Array.from(deduped.values())
    .sort(sortEnvironmentOptions)
    .map((item) => ({ label: `${item.name}${item.version ? ` (${item.version})` : ''}`, value: item.id }));
};

const sortEnvironmentOptions = (left: RuntimeEnvironmentSummary, right: RuntimeEnvironmentSummary) => {
  const leftAuto = left.name?.includes('自动检测');
  const rightAuto = right.name?.includes('自动检测');
  if (leftAuto !== rightAuto) {
    return leftAuto ? 1 : -1;
  }
  return String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN');
};

export default function PipelineAdminPage() {
  const [projects, setProjects] = useState<PipelineSummary['project'][]>([]);
  const [hosts, setHosts] = useState<HostSummary[]>([]);
  const [templates, setTemplates] = useState<TemplateSummary[]>([]);
  const [runtimeEnvironments, setRuntimeEnvironments] = useState<RuntimeEnvironmentSummary[]>([]);
  const [pipelines, setPipelines] = useState<PipelineSummary[]>([]);
  const [form, setForm] = useState<PipelineFormState>(emptyPipeline);
  const [editingId, setEditingId] = useState<number>();
  const [modalOpen, setModalOpen] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);

  const loadData = async () => {
    const [projectResponse, hostResponse, templateResponse, runtimeEnvironmentsResponse, pipelineResponse] = await Promise.all([
      projectsApi.list(),
      hostsApi.list(true),
      templatesApi.list(),
      runtimeEnvironmentsApi.list(),
      pipelinesApi.list(),
    ]);
    setProjects(projectResponse);
    setHosts(hostResponse);
    setTemplates(templateResponse);
    setRuntimeEnvironments(runtimeEnvironmentsResponse);
    setPipelines(pipelineResponse);
  };

  useEffect(() => {
    loadData().catch(() => message.error('加载流水线数据失败'));
  }, []);

  const openCreate = () => {
    setEditingId(undefined);
    setForm(emptyPipeline);
    setCurrentStep(0);
    setModalOpen(true);
  };

  const openEdit = (record: PipelineSummary) => {
    setEditingId(record.id);
    setForm({
      name: record.name || '',
      description: record.description || '',
      projectId: record.project?.id || undefined,
      templateId: record.template?.id || undefined,
      targetHostId: record.targetHost?.id || undefined,
      defaultBranch: record.defaultBranch || 'main',
      variablesJson: parseVariablesJson(record.variablesJson),
      javaEnvironmentId: record.javaEnvironment?.id || undefined,
      nodeEnvironmentId: record.nodeEnvironment?.id || undefined,
      mavenEnvironmentId: record.mavenEnvironment?.id || undefined,
    });
    setCurrentStep(0);
    setModalOpen(true);
  };

  const savePipeline = async () => {
    const payload: PipelinePayload = {
      ...form,
      variablesJson: JSON.stringify(form.variablesJson || {}, null, 2),
    };
    if (editingId) {
      await pipelinesApi.update(editingId, payload);
    } else {
      await pipelinesApi.create(payload);
    }
    setForm(emptyPipeline);
    setEditingId(undefined);
    setCurrentStep(0);
    setModalOpen(false);
    await loadData();
    message.success(editingId ? '流水线已更新' : '流水线已创建');
  };

  const removePipeline = async (id: number) => {
    await pipelinesApi.remove(id);
    await loadData();
    message.success('流水线已删除');
  };

  const selectedTemplate = useMemo(
    () => templates.find((item) => item.id === form.templateId),
    [templates, form.templateId],
  );

  const selectedTargetHost = useMemo(
    () => hosts.find((item) => item.id === form.targetHostId),
    [hosts, form.targetHostId],
  );

  const localHost = useMemo(
    () => hosts.find((item) => item.builtIn) || hosts.find((item) => item.type === 'LOCAL'),
    [hosts],
  );

  const selectedTemplateVariables = useMemo(
    () => parseVariablesSchema(selectedTemplate?.variablesSchema),
    [selectedTemplate],
  );

  const requiredEnvironmentTypes = useMemo(
    () => getRequiredEnvironmentTypes(selectedTemplate?.templateType),
    [selectedTemplate],
  );

  const tableData = useMemo(() => pipelines.map((item) => ({
    ...item,
    parsedVariablesJson: parseVariablesJson(item.variablesJson),
    parsedTemplateVariables: parseVariablesSchema(item.template?.variablesSchema),
  })), [pipelines]);

  const javaOptions = useMemo(
    () => getLocalBuildEnvironmentOptions(runtimeEnvironments, 'JAVA', localHost?.id),
    [runtimeEnvironments, localHost],
  );
  const nodeOptions = useMemo(
    () => getLocalBuildEnvironmentOptions(runtimeEnvironments, 'NODE', localHost?.id),
    [runtimeEnvironments, localHost],
  );
  const mavenOptions = useMemo(
    () => getLocalBuildEnvironmentOptions(runtimeEnvironments, 'MAVEN', localHost?.id),
    [runtimeEnvironments, localHost],
  );

  return (
    <>
      <PageHeaderBar
        title="流水线管理"
        description="流水线绑定项目、模板和默认变量，是用户端直接可部署的对象。构建始终在本机完成，目标主机只负责接收产物并发布。"
        extra={<Button type="primary" onClick={openCreate}>新建流水线</Button>}
      />
      <Card className="app-card">
        {pipelines.length === 0 ? (
          <EmptyPane description="还没有流水线，点击右上角先把项目和模板组合起来。" />
        ) : (
          <Table
            rowKey="id"
            scroll={{ x: 980 }}
            dataSource={tableData}
            columns={[
              { title: '名称', dataIndex: 'name', width: 180 },
              {
                title: '图标',
                width: 80,
                render: (_, row) => <PipelineIcon type={row.template?.templateType} />,
              },
              { title: '项目', render: (_, row) => row.project?.name },
              { title: '目标主机', render: (_, row) => row.targetHost?.name || '-' },
              { title: '模板', render: (_, row) => row.template?.name },
              { title: '构建 Java', render: (_, row) => row.javaEnvironment?.name || '-' },
              { title: '构建 Node', render: (_, row) => row.nodeEnvironment?.name || '-' },
              { title: '构建 Maven', render: (_, row) => row.mavenEnvironment?.name || '-' },
              { title: '默认分支', dataIndex: 'defaultBranch', width: 120 },
              { title: '描述', dataIndex: 'description' },
              {
                title: '默认变量',
                render: (_, row) => (
                  <Space wrap>
                    {row.parsedTemplateVariables.length === 0 ? <span className="text-slate-400">无</span> : null}
                    {row.parsedTemplateVariables.map((item) => (
                      <Tag key={item.name}>
                        {item.label || item.name}：{row.parsedVariablesJson[item.name] || '-'}
                      </Tag>
                    ))}
                  </Space>
                ),
              },
              {
                title: '操作',
                width: 180,
                render: (_, record) => (
                  <Space>
                    <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
                    <Popconfirm
                      title="确认删除这条流水线吗？"
                      okText="确认"
                      cancelText="取消"
                      onConfirm={() => removePipeline(record.id).catch(() => message.error('删除流水线失败'))}
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
        title={editingId ? '编辑流水线' : '新建流水线'}
        open={modalOpen}
        width={900}
        footer={(
          <div className="flex items-center justify-between">
            <Button
              onClick={() => {
                setModalOpen(false);
                setCurrentStep(0);
              }}
            >
              取消
            </Button>
            <Space>
              {currentStep > 0 ? (
                <Button onClick={() => setCurrentStep((value) => value - 1)}>上一步</Button>
              ) : null}
              {currentStep < 3 ? (
                <Button type="primary" onClick={() => setCurrentStep((value) => value + 1)}>下一步</Button>
              ) : (
                <Button
                  type="primary"
                  onClick={() => savePipeline().catch(() => message.error(editingId ? '更新流水线失败' : '创建流水线失败'))}
                >
                  保存
                </Button>
              )}
            </Space>
          </div>
        )}
        onCancel={() => {
          setModalOpen(false);
          setCurrentStep(0);
        }}
        destroyOnClose
      >
        <div className="space-y-4">
          <Card className="border-slate-200 bg-slate-50">
            <Steps
              size="small"
              responsive
              current={currentStep}
              items={[
                { title: '基础信息', description: '名称、项目、模板、分支' },
                { title: '目标主机', description: '选择发布到哪台主机' },
                { title: '构建环境', description: '选择本机构建用环境' },
                { title: '默认变量', description: '按阶段填写默认值' },
              ]}
            />
          </Card>
          <div>
            {currentStep === 0 ? (
              <Card size="small" title="步骤 1 · 基础信息">
                <Form layout="vertical">
                  <Form.Item label="流水线名称">
                    <Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
                  </Form.Item>
                  <Form.Item label="描述">
                    <Input.TextArea rows={3} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
                  </Form.Item>
                  <Form.Item label="项目">
                    <Select
                      value={form.projectId}
                      options={projects.map((item) => ({ label: item.name, value: item.id }))}
                      onChange={(value) => setForm({ ...form, projectId: value })}
                    />
                  </Form.Item>
                  <Form.Item label="模板">
                    <Select
                      value={form.templateId}
                      options={templates.map((item) => ({ label: item.name, value: item.id }))}
                      onChange={(value) => setForm({
                        ...form,
                        templateId: value,
                        variablesJson: {},
                        javaEnvironmentId: undefined,
                        nodeEnvironmentId: undefined,
                        mavenEnvironmentId: undefined,
                      })}
                    />
                  </Form.Item>
                  <Form.Item label="默认分支">
                    <Input value={form.defaultBranch} onChange={(event) => setForm({ ...form, defaultBranch: event.target.value })} />
                  </Form.Item>
                </Form>
              </Card>
            ) : null}
            {currentStep === 1 ? (
              <Card size="small" title="步骤 2 · 目标主机">
                <Form layout="vertical">
                  <Form.Item label="目标主机">
                    <Select
                      value={form.targetHostId}
                      options={hosts.map((item) => ({ label: item.name, value: item.id }))}
                      onChange={(value) => setForm({
                        ...form,
                        targetHostId: value,
                        javaEnvironmentId: undefined,
                        nodeEnvironmentId: undefined,
                        mavenEnvironmentId: undefined,
                      })}
                    />
                  </Form.Item>
                </Form>
                {selectedTargetHost ? (
                  <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                    当前部署目标：{selectedTargetHost.name}
                  </div>
                ) : null}
              </Card>
            ) : null}
            {currentStep === 2 ? (
              <Card size="small" title="步骤 3 · 构建环境">
                {localHost ? (
                  <div className="mb-4 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                    构建会固定在本机完成，不会跟随目标主机切换。当前使用的构建主机：{localHost.name}
                  </div>
                ) : null}
                <Form layout="vertical">
                  {requiredEnvironmentTypes.includes('JAVA') ? (
                    <Form.Item label="本机构建 Java 环境">
                      <Select
                        allowClear
                        value={form.javaEnvironmentId}
                        options={javaOptions}
                        onChange={(value) => setForm({ ...form, javaEnvironmentId: value })}
                        placeholder="请选择本机构建用的 Java 环境"
                      />
                    </Form.Item>
                  ) : null}
                  {requiredEnvironmentTypes.includes('NODE') ? (
                    <Form.Item label="本机构建 Node 环境">
                      <Select
                        allowClear
                        value={form.nodeEnvironmentId}
                        options={nodeOptions}
                        onChange={(value) => setForm({ ...form, nodeEnvironmentId: value })}
                        placeholder="请选择本机构建用的 Node 环境"
                      />
                    </Form.Item>
                  ) : null}
                  {requiredEnvironmentTypes.includes('MAVEN') ? (
                    <Form.Item label="本机构建 Maven 环境">
                      <Select
                        allowClear
                        value={form.mavenEnvironmentId}
                        options={mavenOptions}
                        onChange={(value) => setForm({ ...form, mavenEnvironmentId: value })}
                        placeholder="请选择本机构建用的 Maven 环境"
                      />
                    </Form.Item>
                  ) : null}
                </Form>
              </Card>
            ) : null}
            {currentStep === 3 ? (
              <Card size="small" title="步骤 4 · 默认变量">
                <PipelineVariablesEditor
                  variables={selectedTemplateVariables}
                  values={form.variablesJson}
                  onChange={(value) => setForm({ ...form, variablesJson: value })}
                />
              </Card>
            ) : null}
          </div>
        </div>
      </Modal>
    </>
  );
}
