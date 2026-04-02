import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Divider, Form, Input, Modal, Popconfirm, Select, Space, Steps, Switch, Table, Tag, message } from 'antd';
import { templatesApi } from '../../api/templates';
import type { TemplatePayload } from '../../api/types';
import BooleanBadge from '../../components/BooleanBadge';
import EmptyPane from '../../components/EmptyPane';
import JsonEditor from '../../components/JsonEditor';
import PageHeaderBar from '../../components/PageHeaderBar';
import PipelineIcon, { templateTypeOptions } from '../../components/PipelineIcon';
import TemplateVariablesEditor from '../../components/TemplateVariablesEditor';
import { PHASE_LABEL_MAP, PHASE_TAG_COLOR_MAP, sortByPhase } from '../../utils/tagColors';

interface TemplateFormState {
  name: string;
  description: string;
  templateType: string;
  variablesSchema: Array<{
    name: string;
    label?: string;
    placeholder?: string;
    required?: boolean;
    phase?: 'build' | 'deploy' | 'shared';
  }>;
  buildScriptContent: string;
  deployScriptContent: string;
  monitorProcess: boolean;
}

const emptyTemplate: TemplateFormState = {
  name: '',
  description: '',
  templateType: 'generic',
  variablesSchema: [],
  buildScriptContent: 'set -e\n',
  deployScriptContent: '',
  monitorProcess: false,
};

const parseVariablesSchema = (content) => {
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

const extractVariableNames = (content) => {
  if (!content) {
    return [];
  }
  const matches = content.match(/{{\s*([a-zA-Z0-9_]+)\s*}}/g) || [];
  return Array.from(new Set(matches.map((item) => item.replace(/[{}]/g, '').trim())));
};

const reservedVariables = [
  'branch',
  'gitUrl',
  'gitRepositoryUrl',
  'projectName',
  'pipelineName',
  'applicationName',
  'springProfile',
  'runtimeConfigYaml',
  'runtimeConfigYamlBase64',
  'runtimeConfigFilePath',
  'springBootArgs',
  'workspaceRoot',
  'buildWorkspaceRoot',
  'deployWorkspaceRoot',
  'deploymentId',
  'artifactDir',
];

export default function TemplateAdminPage() {
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<TemplateFormState>(emptyTemplate);
  const [editingId, setEditingId] = useState();
  const [modalOpen, setModalOpen] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [templateTypeFilter, setTemplateTypeFilter] = useState<string>();
  const [monitorFilter, setMonitorFilter] = useState<string>();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  const loadTemplates = async () => {
    setLoading(true);
    try {
      setTemplates(await templatesApi.list());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTemplates().catch(() => message.error('加载模板失败'));
  }, []);

  const openCreate = () => {
    setEditingId(undefined);
    setForm(emptyTemplate);
    setCurrentStep(0);
    setModalOpen(true);
  };

  const openEdit = (record) => {
    setEditingId(record.id);
    setForm({
      name: record.name || '',
      description: record.description || '',
      templateType: record.templateType || 'generic',
      variablesSchema: parseVariablesSchema(record.variablesSchema),
      buildScriptContent: record.buildScriptContent || 'set -e\n',
      deployScriptContent: record.deployScriptContent || '',
      monitorProcess: Boolean(record.monitorProcess),
    });
    setCurrentStep(0);
    setModalOpen(true);
  };

  const saveTemplate = async () => {
    const normalizedVariables = form.variablesSchema
      .map((item) => ({
        name: (item.name || '').trim(),
        label: (item.label || '').trim(),
        placeholder: (item.placeholder || '').trim(),
        required: Boolean(item.required),
        phase: item.phase || 'shared',
      }))
      .filter((item) => item.name);

    const payload: TemplatePayload = {
      ...form,
      variablesSchema: JSON.stringify(normalizedVariables, null, 2),
      buildScriptContent: form.buildScriptContent,
      deployScriptContent: form.deployScriptContent,
      monitorProcess: Boolean(form.monitorProcess),
    };

    if (editingId) {
      await templatesApi.update(editingId, payload);
    } else {
      await templatesApi.create(payload);
    }
    setForm(emptyTemplate);
    setEditingId(undefined);
    setCurrentStep(0);
    setModalOpen(false);
    await loadTemplates();
    message.success(editingId ? '模板已更新' : '模板已创建');
  };

  const removeTemplate = async (id) => {
    await templatesApi.remove(id);
    await loadTemplates();
    message.success('模板已删除');
  };

  const syncVariablesFromScript = (phase) => {
    const buildNames = extractVariableNames(form.buildScriptContent).filter((name) => !reservedVariables.includes(name));
    const deployNames = extractVariableNames(form.deployScriptContent).filter((name) => !reservedVariables.includes(name));
    const targetNames = phase === 'build' ? buildNames : deployNames;
    const currentMap = new Map((form.variablesSchema || []).map((item) => [item.name, item]));

    const nextMap = new Map();
    (form.variablesSchema || []).forEach((item) => {
      if ((item.phase || 'shared') !== phase) {
        nextMap.set(item.name, item);
      }
    });

    targetNames.forEach((name) => {
      const inBuild = buildNames.includes(name);
      const inDeploy = deployNames.includes(name);
      const resolvedPhase = inBuild && inDeploy ? 'shared' : (inBuild ? 'build' : 'deploy');
      nextMap.set(name, {
        ...(currentMap.get(name) || {
          name,
          label: '',
          placeholder: '',
          required: false,
        }),
        phase: resolvedPhase,
      });
    });

    const next = Array.from(nextMap.values()).sort((left, right) => String(left.name).localeCompare(String(right.name), 'zh-CN'));
    setForm((previous) => ({
      ...previous,
      variablesSchema: next,
    }));
    message.success(phase === 'build' ? '已根据构建脚本同步变量' : '已根据发布脚本同步变量');
  };

  const buildVariables = useMemo(
    () => (form.variablesSchema || []).filter((item) => ['build', 'shared'].includes(item.phase || 'shared')),
    [form.variablesSchema],
  );

  const deployVariables = useMemo(
    () => (form.variablesSchema || []).filter((item) => ['deploy', 'shared'].includes(item.phase || 'shared')),
    [form.variablesSchema],
  );

  const tableData = useMemo(() => templates.map((item) => ({
    ...item,
    parsedVariables: parseVariablesSchema(item.variablesSchema),
  })), [templates]);

  const filteredTemplates = useMemo(() => tableData.filter((item) => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    if (normalizedKeyword) {
      const matched = [item.name, item.description]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedKeyword));
      if (!matched) {
        return false;
      }
    }
    if (templateTypeFilter && item.templateType !== templateTypeFilter) {
      return false;
    }
    if (monitorFilter != null) {
      const expected = monitorFilter === 'true';
      if (Boolean(item.monitorProcess) !== expected) {
        return false;
      }
    }
    return true;
  }), [tableData, keyword, templateTypeFilter, monitorFilter]);

  return (
    <>
      <PageHeaderBar
        title="模板管理"
        description="管理部署模板、本机构建脚本、发布脚本与变量定义。"
        extra={(
          <Space>
            <Button onClick={() => loadTemplates().catch(() => message.error('加载模板失败'))}>刷新</Button>
            <Button type="primary" onClick={openCreate}>新建模板</Button>
          </Space>
        )}
      />
      <Card className="app-card">
        <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-4">
          <Input
            value={keyword}
            placeholder="搜索模板名称 / 描述"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={templateTypeFilter}
            placeholder="筛选模板类型"
            options={templateTypeOptions}
            onChange={(value) => {
              setTemplateTypeFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={monitorFilter}
            placeholder="筛选监控进程"
            options={[
              { label: '监控进程', value: 'true' },
              { label: '不监控进程', value: 'false' },
            ]}
            onChange={(value) => {
              setMonitorFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <div className="flex items-center">
            <Button
              onClick={() => {
                setKeyword('');
                setTemplateTypeFilter(undefined);
                setMonitorFilter(undefined);
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
          scroll={{ x: 960 }}
          dataSource={filteredTemplates}
          locale={{ emptyText: <EmptyPane description="还没有模板，点击右上角先沉淀一套真实部署脚本。" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: filteredTemplates.length,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (current, pageSize) => setPagination({ current, pageSize }),
          }}
          columns={[
              { title: '名称', dataIndex: 'name', width: 180 },
              {
                title: '模板类型',
                width: 140,
                render: (_, record) => (
                  <div className="flex items-center gap-2">
                    <PipelineIcon type={record.templateType} />
                    <span>{templateTypeOptions.find((item) => item.value === record.templateType)?.label || '通用'}</span>
                  </div>
                ),
              },
              { title: '描述', dataIndex: 'description' },
              {
                title: '监控进程',
                width: 120,
                render: (_, record) => <BooleanBadge value={Boolean(record.monitorProcess)} />,
              },
              {
                title: '变量定义',
                render: (_, record) => (
                  <Space wrap>
                    {record.parsedVariables.length === 0 ? <span className="text-slate-400">无</span> : null}
                    {sortByPhase(record.parsedVariables).map((item) => (
                      <Tag
                        key={item.name}
                        style={{
                          backgroundColor: PHASE_TAG_COLOR_MAP[item.phase || 'shared'] || PHASE_TAG_COLOR_MAP.shared,
                          color: '#fff',
                          borderColor: 'transparent',
                        }}
                        className="!border-0"
                      >
                        [{PHASE_LABEL_MAP[item.phase || 'shared'] || '共用'}] {item.label || item.name}
                        {item.required ? ' *' : ''}
                      </Tag>
                    ))}
                  </Space>
                ),
              },
              {
                title: '构建脚本',
                render: (_, record) => (
                  <pre className="table-code-preview">{record.buildScriptContent}</pre>
                ),
              },
              {
                title: '发布脚本',
                render: (_, record) => (
                  record.deployScriptContent ? (
                    <pre className="table-code-preview">{record.deployScriptContent}</pre>
                  ) : (
                    <span className="text-slate-400">无独立发布脚本</span>
                  )
                ),
              },
              {
                title: '操作',
                width: 180,
                render: (_, record) => (
                  <Space>
                    <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
                    <Popconfirm
                      title="确认删除这个模板吗？"
                      okText="确认"
                      cancelText="取消"
                      onConfirm={() => removeTemplate(record.id).catch(() => message.error('删除模板失败'))}
                    >
                      <Button size="small" danger>删除</Button>
                    </Popconfirm>
                  </Space>
                ),
              },
          ]}
        />
      </Card>
      <Modal
        title={editingId ? '编辑模板' : '新建模板'}
        open={modalOpen}
        width={980}
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
                  onClick={() => saveTemplate().catch(() => message.error(editingId ? '更新模板失败' : '创建模板失败'))}
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
              onChange={(value) => setCurrentStep(value)}
              items={[
                { title: '基础信息', description: '模板名称、类型、描述' },
                { title: '构建', description: '本机构建脚本' },
                { title: '发布', description: '目标主机发布脚本与进程监控' },
                { title: '变量', description: '从脚本提取并整理变量' },
              ]}
            />
          </Card>
          <div>
            {currentStep === 0 ? (
              <Card size="small" title="步骤 1 · 基础信息">
              <Form layout="vertical">
                <Form.Item label="模板名称">
                  <Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
                </Form.Item>
                <Form.Item label="描述">
                  <Input.TextArea rows={3} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
                </Form.Item>
                <Form.Item label="模板类型">
                  <Input.Group compact>
                    <div className="mb-3 flex items-center gap-3">
                      <PipelineIcon type={form.templateType} />
                      <div className="flex-1">
                        <Form.Item noStyle>
                          <select
                            className="w-full rounded-xl border border-slate-200 px-3 py-2"
                            value={form.templateType}
                            onChange={(event) => setForm({ ...form, templateType: event.target.value })}
                          >
                            {templateTypeOptions.map((item) => (
                              <option key={item.value} value={item.value}>{item.label}</option>
                            ))}
                          </select>
                        </Form.Item>
                      </div>
                    </div>
                  </Input.Group>
                </Form.Item>
              </Form>
              </Card>
            ) : null}
            {currentStep === 1 ? (
              <Card size="small" title="步骤 2 · 构建">
                <div>
                  <div className="mb-2 font-medium text-slate-800">2.1 本机构建脚本</div>
                  <div className="mb-2 text-xs text-slate-500">
                    这里负责 git clone、npm、maven、打包和准备产物。构建产物建议统一输出到 <code>{'{{artifactDir}}'}</code>。
                  </div>
                  <JsonEditor
                    rows={14}
                    language="shell"
                    value={form.buildScriptContent}
                    onChange={(value) => setForm({ ...form, buildScriptContent: value })}
                  />
                  <div className="mt-4">
                    <TemplateVariablesEditor
                      title="2.2 构建变量"
                      description="这些变量用于本机构建阶段。先在构建脚本里写占位符，再同步变量并补充说明。"
                      phase="build"
                      value={buildVariables}
                      onChange={(value) => {
                        const others = (form.variablesSchema || []).filter((item) => !['build', 'shared'].includes(item.phase || 'shared'));
                        setForm({ ...form, variablesSchema: [...others, ...value] });
                      }}
                    />
                    <div className="mt-3">
                      <Button onClick={() => syncVariablesFromScript('build')}>从构建脚本提取变量</Button>
                    </div>
                  </div>
                </div>
              </Card>
            ) : null}
            {currentStep === 2 ? (
              <Card size="small" title="步骤 3 · 发布">
                <div className="space-y-4">
                <div>
                  <div className="mb-2 font-medium text-slate-800">3.1 目标主机发布脚本</div>
                  <div className="mb-2 text-xs text-slate-500">
                    这里运行在目标主机上，只负责接收 <code>{'{{artifactDir}}'}</code> 里的产物并发布。留空表示当前模板没有独立的发布阶段。
                  </div>
                  <JsonEditor
                    rows={10}
                    language="shell"
                    value={form.deployScriptContent}
                    onChange={(value) => setForm({ ...form, deployScriptContent: value })}
                  />
                  <div className="mt-4">
                    <TemplateVariablesEditor
                      title="3.2 发布变量"
                      description="这些变量用于目标主机发布阶段，通常对应远程目录、远程启动命令等。"
                      phase="deploy"
                      value={deployVariables}
                      onChange={(value) => {
                        const others = (form.variablesSchema || []).filter((item) => !['deploy', 'shared'].includes(item.phase || 'shared'));
                        setForm({ ...form, variablesSchema: [...others, ...value] });
                      }}
                    />
                    <div className="mt-3">
                      <Button onClick={() => syncVariablesFromScript('deploy')}>从发布脚本提取变量</Button>
                    </div>
                  </div>
                </div>
                <Divider className="my-0" />
                <Form layout="vertical">
                  <Form.Item label="3.3 监控进程">
                    <Switch
                      checked={Boolean(form.monitorProcess)}
                      checkedChildren="是"
                      unCheckedChildren="否"
                      onChange={(checked) => setForm({ ...form, monitorProcess: checked })}
                    />
                  </Form.Item>
                  {form.monitorProcess ? (
                    <div className="text-xs text-slate-500">
                      开启后表示系统会在发布后尝试接管服务。更精确的启动关键字和启动超时配置放在流水线上，按具体部署环境单独设置。
                    </div>
                  ) : (
                    <div className="text-xs text-slate-500">
                      只有发布脚本最终会拉起长期运行的进程时，才需要开启监控进程。
                    </div>
                  )}
                </Form>
              </div>
              </Card>
            ) : null}
            {currentStep === 3 ? (
              <Card size="small" title="步骤 4 · 变量总览">
                <div>
                  <div className="mb-2 flex items-center justify-between gap-3">
                    <div>
                      <div className="font-medium text-slate-800">4.1 变量总览</div>
                      <div className="mt-1 text-xs text-slate-500">
                        这里汇总展示模板对外暴露的变量，流水线填写默认值时会按构建 / 发布阶段分组显示。
                      </div>
                    </div>
                  </div>
                  <TemplateVariablesEditor
                    value={form.variablesSchema}
                    onChange={(value) => setForm({ ...form, variablesSchema: value })}
                  />
                </div>
              </Card>
            ) : null}
          </div>
        </div>
      </Modal>
    </>
  );
}
