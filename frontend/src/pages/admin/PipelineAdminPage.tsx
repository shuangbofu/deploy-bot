import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Steps, Table, Tag, message } from 'antd';
import { hostsApi } from '../../api/hosts';
import { notificationsApi } from '../../api/notifications';
import { pipelinesApi } from '../../api/pipelines';
import { projectsApi } from '../../api/projects';
import { runtimeEnvironmentsApi } from '../../api/runtimeEnvironments';
import { templatesApi } from '../../api/templates';
import type { PipelinePayload } from '../../api/types';
import EmptyPane from '../../components/EmptyPane';
import JsonEditor from '../../components/JsonEditor';
import PageHeaderBar from '../../components/PageHeaderBar';
import PipelineVariablesEditor from '../../components/PipelineVariablesEditor';
import PipelineIcon, { getRequiredEnvironmentTypes, getRequiredRuntimeEnvironmentTypes } from '../../components/PipelineIcon';
import { copyText } from '../../utils/clipboard';
import { getStableTagColor, PHASE_LABEL_MAP, PHASE_TAG_COLOR_MAP, sortByPhase } from '../../utils/tagColors';
import type {
  HostSummary,
  NotificationBinding,
  NotificationChannelSummary,
  MavenSettingsSummary,
  PipelineSummary,
  RuntimeEnvironmentSummary,
  TemplateSummary,
  TemplateVariableDefinition,
} from '../../types/domain';

interface PipelineFormState {
  name: string;
  description: string;
  tags: string[];
  projectId?: number;
  templateId?: number;
  targetHostId?: number;
  defaultBranch: string;
  variablesJson: Record<string, string>;
  javaEnvironmentId?: number;
  nodeEnvironmentId?: number;
  mavenEnvironmentId?: number;
  mavenSettingsId?: number;
  runtimeJavaEnvironmentId?: number;
  applicationName: string;
  springProfile: string;
  runtimeConfigYaml: string;
  startupKeyword: string;
  startupTimeoutSeconds?: number;
  notificationIds: number[];
}

const emptyPipeline: PipelineFormState = {
  name: '',
  description: '',
  tags: [],
  projectId: undefined,
  templateId: undefined,
  targetHostId: undefined,
  defaultBranch: 'main',
  variablesJson: {},
  javaEnvironmentId: undefined,
  nodeEnvironmentId: undefined,
  mavenEnvironmentId: undefined,
  mavenSettingsId: undefined,
  runtimeJavaEnvironmentId: undefined,
  applicationName: '',
  springProfile: '',
  runtimeConfigYaml: '',
  startupKeyword: '',
  startupTimeoutSeconds: 30,
  notificationIds: [],
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

/**
 * 标签字段在接口里以 JSON 字符串存储，这里统一转成字符串数组。
 */
const parseTagsJson = (content: unknown): string[] => {
  if (!content) {
    return [];
  }
  if (Array.isArray(content)) {
    return content.filter(Boolean).map((item) => String(item).trim()).filter(Boolean);
  }
  try {
    const parsed = JSON.parse(String(content));
    return Array.isArray(parsed) ? parsed.filter(Boolean).map((item) => String(item).trim()).filter(Boolean) : [];
  } catch {
    return [];
  }
};

const parseNotificationBindings = (content: unknown): NotificationBinding[] => {
  if (!content) {
    return [];
  }
  if (Array.isArray(content)) {
    return content.filter((item): item is NotificationBinding => Boolean(item?.notificationId && item?.eventType));
  }
  try {
    const parsed = JSON.parse(String(content));
    return Array.isArray(parsed)
      ? parsed.filter((item): item is NotificationBinding => Boolean(item?.notificationId && item?.eventType))
      : [];
  } catch {
    return [];
  }
};

const deriveNotificationIds = (bindings: NotificationBinding[]) => Array.from(new Set(bindings.map((item) => item.notificationId)));

const getLocalBuildEnvironmentOptions = (
  items: RuntimeEnvironmentSummary[],
  type: RuntimeEnvironmentSummary['type'],
  localHostId?: number,
) => {
  const candidates = items.filter((item) => {
    if (item?.type !== type || item.enabled === false) {
      return false;
    }
    if (item.host == null) {
      return true;
    }
    if (localHostId && item.host.id === localHostId) {
      return true;
    }
    return item.host.type === 'LOCAL' || item.host.builtIn === true;
  });
  const deduped = new Map();

  candidates.forEach((item) => {
    const key = [item.type, item.name || '', item.version || ''].join('::');
    const existing = deduped.get(key);
    if (!existing) {
      deduped.set(key, item);
      return;
    }
    const currentIsLocal = item.host?.id === localHostId || item.host?.type === 'LOCAL' || item.host?.builtIn === true;
    const existingIsLocal = existing.host?.id === localHostId || existing.host?.type === 'LOCAL' || existing.host?.builtIn === true;
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
  const [notifications, setNotifications] = useState<NotificationChannelSummary[]>([]);
  const [mavenSettings, setMavenSettings] = useState<MavenSettingsSummary[]>([]);
  const [runtimeEnvironments, setRuntimeEnvironments] = useState<RuntimeEnvironmentSummary[]>([]);
  const [pipelines, setPipelines] = useState<PipelineSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [availableTags, setAvailableTags] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<PipelineFormState>(emptyPipeline);
  const [editingId, setEditingId] = useState<number>();
  const [modalOpen, setModalOpen] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [projectFilter, setProjectFilter] = useState<number>();
  const [templateFilter, setTemplateFilter] = useState<number>();
  const [hostFilter, setHostFilter] = useState<number>();
  const [tagFilter, setTagFilter] = useState<string[]>();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  const loadMeta = async () => {
    const [projectResponse, hostResponse, templateResponse, runtimeEnvironmentsResponse, notificationResponse, tagResponse] = await Promise.all([
      projectsApi.list(),
      hostsApi.list(true),
      templatesApi.list(),
      runtimeEnvironmentsApi.list(),
      notificationsApi.list(),
      pipelinesApi.listTags(),
    ]);
    setProjects(projectResponse);
    setHosts(hostResponse);
    setTemplates(templateResponse);
    setRuntimeEnvironments(runtimeEnvironmentsResponse);
    setNotifications(notificationResponse);
    setAvailableTags(tagResponse);
  };

  const loadPipelines = async () => {
    setLoading(true);
    try {
      const result = await pipelinesApi.listPage({
        page: pagination.current,
        pageSize: pagination.pageSize,
        keyword: keyword.trim() || undefined,
        projectId: projectFilter,
        templateId: templateFilter,
        hostId: hostFilter,
        tags: tagFilter && tagFilter.length > 0 ? tagFilter : undefined,
      });
      setPipelines(result.items);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMeta().catch(() => message.error('加载流水线数据失败'));
  }, []);

  useEffect(() => {
    loadPipelines().catch(() => message.error('加载流水线数据失败'));
  }, [pagination.current, pagination.pageSize, keyword, projectFilter, templateFilter, hostFilter, tagFilter]);

  useEffect(() => {
    const loadScopedMavenSettings = async () => {
      if (!form.mavenEnvironmentId) {
        setMavenSettings([]);
        return;
      }
      const response = await runtimeEnvironmentsApi.listMavenSettings(form.mavenEnvironmentId);
      setMavenSettings(response);
      if (form.mavenSettingsId && !response.some((item) => item.id === form.mavenSettingsId)) {
        setForm((previous) => ({ ...previous, mavenSettingsId: undefined }));
      }
    };
    loadScopedMavenSettings().catch(() => {
      setMavenSettings([]);
      message.error('加载 Maven settings 失败');
    });
  }, [form.mavenEnvironmentId]);

  const openCreate = () => {
    setEditingId(undefined);
    setForm(emptyPipeline);
    setMavenSettings([]);
    setCurrentStep(0);
    setModalOpen(true);
  };

  const openEdit = (record: PipelineSummary) => {
    setEditingId(record.id);
    setForm({
      name: record.name || '',
      description: record.description || '',
      tags: parseTagsJson(record.tagsJson),
      projectId: record.project?.id || undefined,
      templateId: record.template?.id || undefined,
      targetHostId: record.targetHost?.id || undefined,
      defaultBranch: record.defaultBranch || 'main',
      variablesJson: parseVariablesJson(record.variablesJson),
      javaEnvironmentId: record.javaEnvironment?.id || undefined,
      nodeEnvironmentId: record.nodeEnvironment?.id || undefined,
      mavenEnvironmentId: record.mavenEnvironment?.id || undefined,
      mavenSettingsId: record.mavenSettings?.id || undefined,
      runtimeJavaEnvironmentId: record.runtimeJavaEnvironment?.id || undefined,
      applicationName: record.applicationName || '',
      springProfile: record.springProfile || '',
      runtimeConfigYaml: record.runtimeConfigYaml || '',
      startupKeyword: record.startupKeyword || '',
      startupTimeoutSeconds: record.startupTimeoutSeconds || 30,
      notificationIds: deriveNotificationIds(parseNotificationBindings(record.notificationBindingsJson)),
    });
    setCurrentStep(0);
    setModalOpen(true);
  };

  const savePipeline = async () => {
    const payload: PipelinePayload = {
      ...form,
      variablesJson: JSON.stringify(form.variablesJson || {}, null, 2),
      tagsJson: JSON.stringify(form.tags || [], null, 2),
      notificationBindingsJson: JSON.stringify(
        form.notificationIds
          .map((notificationId) => {
            const notification = notifications.find((item) => item.id === notificationId);
            const eventType = notification?.eventType;
            if (!eventType) {
              return null;
            }
            return { notificationId, eventType };
          })
          .filter(Boolean),
        null,
        2,
      ),
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
    await Promise.all([loadMeta(), loadPipelines()]);
    message.success(editingId ? '流水线已更新' : '流水线已创建');
  };

  const removePipeline = async (id: number) => {
    await pipelinesApi.remove(id);
    await Promise.all([loadMeta(), loadPipelines()]);
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
  const isSpringBootTemplate = useMemo(
    () => ['springboot', 'springboot_frontend'].includes(selectedTemplate?.templateType || ''),
    [selectedTemplate],
  );
  const stepItems = useMemo(() => {
    const items = [
      { key: 'basic', title: '基础信息', description: '名称、项目、模板、分支' },
      { key: 'build', title: '构建环境', description: '选择本机构建用环境' },
      { key: 'target', title: '目标主机', description: '选择发布到哪台主机' },
    ];
    if (isSpringBootTemplate) {
      items.push({ key: 'runtime', title: '运行配置', description: 'Profile / YAML 覆盖' });
    }
    items.push(
      { key: 'variables', title: '变量', description: '按阶段填写变量值' },
      { key: 'notifications', title: '通知配置', description: '绑定要使用的通知配置' },
    );
    return items;
  }, [isSpringBootTemplate]);
  const currentStepKey = stepItems[currentStep]?.key;

  const requiredEnvironmentTypes = useMemo(
    () => getRequiredEnvironmentTypes(selectedTemplate?.templateType),
    [selectedTemplate],
  );
  const requiredRuntimeEnvironmentTypes = useMemo(
    () => getRequiredRuntimeEnvironmentTypes(selectedTemplate?.templateType),
    [selectedTemplate],
  );

  const tableData = useMemo(() => pipelines.map((item) => ({
    ...item,
    parsedVariablesJson: parseVariablesJson(item.variablesJson),
    parsedTemplateVariables: parseVariablesSchema(item.template?.variablesSchema),
    parsedTags: parseTagsJson(item.tagsJson),
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
  const runtimeJavaOptions = useMemo(
    () => runtimeEnvironments
      .filter((item) => item.type === 'JAVA' && item.enabled !== false && item.host?.id === form.targetHostId)
      .sort(sortEnvironmentOptions)
      .map((item) => ({ label: `${item.name}${item.version ? ` (${item.version})` : ''}`, value: item.id })),
    [runtimeEnvironments, form.targetHostId],
  );

  return (
    <>
      <PageHeaderBar
        title="流水线管理"
        description="配置用户可直接部署的流水线，绑定项目、模板、变量和运行环境。构建在本机完成，目标主机负责接收产物并发布。"
        extra={(
          <Space>
            <Button onClick={() => loadPipelines().catch(() => message.error('加载流水线数据失败'))}>刷新</Button>
            <Button type="primary" onClick={openCreate}>新建流水线</Button>
          </Space>
        )}
      />
      <div className="app-page-scroll">
      <Card className="app-card">
        <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-5">
          <Input
            value={keyword}
            placeholder="搜索流水线名称 / 描述 / 默认分支"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={projectFilter}
            placeholder="筛选项目"
            options={projects.map((item) => ({ label: item.name, value: item.id }))}
            onChange={(value) => {
              setProjectFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={templateFilter}
            placeholder="筛选模板"
            options={templates.map((item) => ({ label: item.name, value: item.id }))}
            onChange={(value) => {
              setTemplateFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={hostFilter}
            placeholder="筛选目标主机"
            options={hosts.map((item) => ({ label: item.name, value: item.id }))}
            onChange={(value) => {
              setHostFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <div className="flex items-center">
            <Button
              onClick={() => {
                setKeyword('');
                setProjectFilter(undefined);
                setTemplateFilter(undefined);
                setHostFilter(undefined);
                setTagFilter(undefined);
                setPagination((previous) => ({ ...previous, current: 1 }));
              }}
            >
              重置条件
            </Button>
          </div>
        </div>
        {availableTags.length > 0 ? (
          <div className="mb-4 flex flex-wrap gap-2">
            {availableTags.map((tag) => {
              const active = Boolean(tagFilter?.includes(tag));
              return (
                <Tag
                  key={tag}
                  style={{
                    backgroundColor: getStableTagColor(tag),
                    color: '#fff',
                    borderColor: 'transparent',
                    opacity: active ? 1 : 0.55,
                  }}
                  className="cursor-pointer select-none !border-0 !px-3 !py-1"
                  onClick={() => {
                    setTagFilter((previous) => {
                      const next = previous?.includes(tag)
                        ? previous.filter((item) => item !== tag)
                        : [...(previous || []), tag];
                      setPagination((current) => ({ ...current, current: 1 }));
                      return next.length > 0 ? next : undefined;
                    });
                  }}
                >
                  {tag}
                </Tag>
              );
            })}
          </div>
        ) : null}
        <Table
          rowKey="id"
          loading={loading}
          scroll={{ x: 1720 }}
          dataSource={tableData}
          locale={{ emptyText: <EmptyPane description="还没有流水线，点击右上角先把项目和模板组合起来。" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (current, pageSize) => setPagination({ current, pageSize }),
          }}
          columns={[
              {
                title: '名称',
                width: 200,
                onHeaderCell: () => ({
                  style: {
                    minWidth: 200,
                    maxWidth: 100,
                  },
                }),
                render: (_, row) => (
                  <div className="flex items-center gap-0.5">
                    <div className="scale-[0.82] origin-left">
                      <PipelineIcon type={row.template?.templateType} />
                    </div>
                    <span>{row.name}</span>
                  </div>
                ),
              },
              { title: '项目', render: (_, row) => row.project?.name },
              { title: '目标主机', render: (_, row) => row.targetHost?.name || '-' },
              { title: '模板',  onCell: () => ({
                                               style: {
                                                 minWidth: 150,
                                                 maxWidth: 300,
                                               },
                                             }), render: (_, row) => row.template?.name },
              {
                title: '描述',
                dataIndex: 'description',
                width: 360,
                onCell: () => ({
                  style: {
                    minWidth: 250,
                    maxWidth: 360,
                  },
                }),
                render: (value) => value ? (
                  <div className="py-1 text-sm leading-6 whitespace-normal break-words text-slate-600 line-clamp-2" title={value}>
                    {value}
                  </div>
                ) : '-',
              },
              {
                title: '标签',
                width: 180,
                render: (_, row) => row.parsedTags.length > 0
                  ? (
                    <Space wrap>
                      {row.parsedTags.map((tag: string) => (
                        <Tag
                          key={tag}
                          style={{
                            backgroundColor: getStableTagColor(tag),
                            color: '#fff',
                            borderColor: 'transparent',
                          }}
                          className="!border-0"
                        >
                          {tag}
                        </Tag>
                      ))}
                    </Space>
                  )
                  : '-',
              },
              { title: '默认分支', dataIndex: 'defaultBranch', width: 120 },
              {
                title: '变量',
                render: (_, row) => (
                    <Space wrap>
                      {row.parsedTemplateVariables.length === 0 ? <span className="text-slate-400">无</span> : null}
                    {sortByPhase(row.parsedTemplateVariables).map((item) => (
                      <Tag
                        key={item.name}
                        style={{
                          backgroundColor: `${(PHASE_TAG_COLOR_MAP[item.phase || 'shared'] || PHASE_TAG_COLOR_MAP.shared)}1A`,
                          color: '#334155',
                          borderColor: 'transparent',
                        }}
                        className="!border-0 !pl-0 !py-0"
                      >
                        <span
                          className="mr-1.5 inline-flex items-center rounded-md px-2 py-1 text-xs font-normal text-white"
                          style={{
                            backgroundColor: PHASE_TAG_COLOR_MAP[item.phase || 'shared'] || PHASE_TAG_COLOR_MAP.shared,
                            borderTopRightRadius: 0,
                            borderBottomRightRadius: 0,
                          }}
                        >
                          <span>[{PHASE_LABEL_MAP[item.phase || 'shared'] || '共用'}]</span>
                          <span className="ml-1">{item.label || item.name}</span>
                        </span>
                        <button
                          type="button"
                          className="cursor-pointer rounded-sm bg-transparent px-1 py-0 text-slate-700 transition-colors hover:text-slate-900"
                          onClick={async () => {
                            const value = row.parsedVariablesJson[item.name] || '-';
                            try {
                              await copyText(value);
                              message.success(`已复制：${value}`);
                            } catch {
                              message.error('复制失败');
                            }
                          }}
                        >
                          {row.parsedVariablesJson[item.name] || '-'}
                        </button>
                      </Tag>
                    ))}
                  </Space>
                ),
              },
              {
                title: '环境版本',
                width: 360,
                render: (_, row) => {
                  const items = [
                    row.javaEnvironment ? `构建 Java：${row.javaEnvironment.name}` : null,
                    row.nodeEnvironment ? `构建 Node：${row.nodeEnvironment.name}` : null,
                    row.mavenEnvironment ? `构建 Maven：${row.mavenEnvironment.name}` : null,
                    row.runtimeJavaEnvironment ? `运行 Java：${row.runtimeJavaEnvironment.name}` : null,
                  ].filter(Boolean);
                  return items.length > 0 ? (
                    <div className="space-y-2 py-1 text-sm leading-6 text-slate-600">
                      {items.map((item) => (
                        <div key={item} className="truncate" title={item}>
                          {item}
                        </div>
                      ))}
                    </div>
                  ) : '-';
                },
              },
              {
                title: '监控配置',
                width: 380,
                render: (_, row) => {
                  const items = [
                    row.applicationName ? `应用名：${row.applicationName}` : null,
                    row.springProfile ? `Profile：${row.springProfile}` : null,
                    row.startupKeyword ? `关键字：${row.startupKeyword}` : null,
                    row.startupTimeoutSeconds ? `超时：${row.startupTimeoutSeconds} 秒` : null,
                  ].filter(Boolean);
                  return items.length > 0 ? (
                    <div className="space-y-2 py-1 text-sm leading-6 text-slate-600">
                      {items.map((item) => (
                        <div key={item} className="truncate" title={item}>
                          {item}
                        </div>
                      ))}
                    </div>
                  ) : '-';
                },
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
                      onConfirm={() => removePipeline(record.id)}
                    >
                      <Button size="small" danger>删除</Button>
                    </Popconfirm>
                  </Space>
                ),
              },
          ]}
        />
      </Card>
      </div>
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
              {editingId ? (
                <Button
                  type="primary"
                  onClick={() => savePipeline().catch(() => undefined)}
                >
                  保存
                </Button>
              ) : null}
              {!editingId && currentStep === stepItems.length - 1 ? (
                <Button
                  type="primary"
                  onClick={() => savePipeline().catch(() => undefined)}
                >
                  创建
                </Button>
              ) : null}
              {currentStep < stepItems.length - 1 ? (
                <Button onClick={() => setCurrentStep((value) => value + 1)}>下一步</Button>
              ) : null}
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
              items={stepItems}
            />
          </Card>
          <div>
            {currentStepKey === 'basic' ? (
              <Card size="small" title="步骤 1 · 基础信息">
                <Form layout="vertical">
                  <Form.Item label="流水线名称">
                    <Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
                  </Form.Item>
                  <Form.Item label="描述">
                    <Input.TextArea rows={3} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
                  </Form.Item>
                  <Form.Item label="标签">
                    <Select
                      mode="tags"
                      value={form.tags}
                      placeholder="输入后回车，可用于业务线、端别、环境等筛选"
                      onChange={(value) => setForm({ ...form, tags: value })}
                    />
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
                        mavenSettingsId: undefined,
                        runtimeJavaEnvironmentId: undefined,
                        applicationName: '',
                        springProfile: '',
                        runtimeConfigYaml: '',
                        startupKeyword: '',
                        startupTimeoutSeconds: 30,
                        notificationIds: [],
                      })}
                    />
                  </Form.Item>
                  <Form.Item label="默认分支">
                    <Input value={form.defaultBranch} onChange={(event) => setForm({ ...form, defaultBranch: event.target.value })} />
                  </Form.Item>
                </Form>
              </Card>
            ) : null}
            {currentStepKey === 'build' ? (
              <Card size="small" title="步骤 2 · 构建环境">
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
                        notFoundContent="当前没有可用的本机 Node 环境，请先到主机管理 -> 本机 -> 环境管理里配置。"
                      />
                    </Form.Item>
                  ) : null}
                  {requiredEnvironmentTypes.includes('MAVEN') ? (
                    <>
                      <Form.Item label="本机构建 Maven 环境">
                        <Select
                          allowClear
                          value={form.mavenEnvironmentId}
                          options={mavenOptions}
                          onChange={(value) => setForm({ ...form, mavenEnvironmentId: value, mavenSettingsId: undefined })}
                          placeholder="请选择本机构建用的 Maven 环境"
                        />
                      </Form.Item>
                      <Form.Item label="构建 Maven Settings">
                        <Select
                          allowClear
                          value={form.mavenSettingsId}
                          options={mavenSettings.filter((item) => item.enabled).map((item) => ({
                            label: item.isDefault ? `${item.name}（默认）` : item.name,
                            value: item.id,
                          }))}
                          onChange={(value) => setForm({ ...form, mavenSettingsId: value })}
                          placeholder={form.mavenEnvironmentId ? '可选。选择后构建时会自动对 mvn 注入 -s settings.xml' : '请先选择 Maven 环境'}
                          disabled={!form.mavenEnvironmentId}
                          notFoundContent={form.mavenEnvironmentId ? '当前 Maven 环境下还没有 settings.xml，请先到环境管理里配置。' : undefined}
                        />
                      </Form.Item>
                    </>
                  ) : null}
                </Form>
              </Card>
            ) : null}
            {currentStepKey === 'target' ? (
              <Card size="small" title="步骤 3 · 目标主机">
                <Form layout="vertical">
                  <Form.Item label="目标主机">
                    <Select
                      value={form.targetHostId}
                      options={hosts.map((item) => ({ label: item.name, value: item.id }))}
                      onChange={(value) => setForm({
                        ...form,
                        targetHostId: value,
                        runtimeJavaEnvironmentId: undefined,
                      })}
                    />
                  </Form.Item>
                  {requiredRuntimeEnvironmentTypes.includes('JAVA') ? (
                    <Form.Item label="目标主机运行 Java 环境">
                      <Select
                        allowClear
                        value={form.runtimeJavaEnvironmentId}
                        options={runtimeJavaOptions}
                        onChange={(value) => setForm({ ...form, runtimeJavaEnvironmentId: value })}
                        placeholder="请选择目标主机运行应用时使用的 Java 环境"
                      />
                    </Form.Item>
                  ) : null}
                  {selectedTemplate?.monitorProcess ? (
                    <>
                      <Form.Item label="应用名">
                        <Input
                          value={form.applicationName}
                          onChange={(event) => setForm({ ...form, applicationName: event.target.value })}
                          placeholder="例如：deploy-bot-backend"
                        />
                      </Form.Item>
                      <Form.Item label="启动关键字">
                        <Input
                          value={form.startupKeyword}
                          onChange={(event) => setForm({ ...form, startupKeyword: event.target.value })}
                          placeholder="例如：Started DeployBotApplication in"
                        />
                      </Form.Item>
                      <Form.Item label="启动超时（秒）">
                        <Input
                          type="number"
                          min={5}
                          value={form.startupTimeoutSeconds}
                          onChange={(event) => setForm({
                            ...form,
                            startupTimeoutSeconds: event.target.value ? Number(event.target.value) : undefined,
                          })}
                          placeholder="30"
                        />
                      </Form.Item>
                      <div className="mb-4 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                        开启服务监测的模板会在发布后进入启动观察窗口。应用名会作为系统内置变量注入模板，用于生成唯一产物名；启动关键字只用于判断服务是否真正完成启动。
                      </div>
                    </>
                  ) : null}
                </Form>
                {selectedTargetHost ? (
                  <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                    当前部署目标：{selectedTargetHost.name}
                  </div>
                ) : null}
              </Card>
            ) : null}
            {currentStepKey === 'runtime' ? (
              <Card size="small" title="步骤 4 · 运行配置">
                <Form layout="vertical">
                  <>
                    <Form.Item label="Spring Profile">
                      <Input
                        value={form.springProfile}
                        onChange={(event) => setForm({ ...form, springProfile: event.target.value })}
                        placeholder="例如：prod / test / dev / local"
                      />
                    </Form.Item>
                    <Form.Item label="运行配置 YAML">
                      <JsonEditor
                        rows={14}
                        language="yaml"
                        value={form.runtimeConfigYaml}
                        onChange={(value) => setForm({ ...form, runtimeConfigYaml: value })}
                        placeholder={'例如：\nspring:\n  datasource:\n    url: jdbc:mysql://...'}
                      />
                    </Form.Item>
                    <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                      这份 YAML 会在发布阶段写入目标主机，并通过附加配置参数自动生效；如果只填 Profile，系统会自动把它加到启动参数里。
                    </div>
                  </>
                </Form>
              </Card>
            ) : null}
            {currentStepKey === 'variables' ? (
              <Card size="small" title={`步骤 ${stepItems.findIndex((item) => item.key === 'variables') + 1} · 变量`}>
                <PipelineVariablesEditor
                  variables={selectedTemplateVariables}
                  values={form.variablesJson}
                  onChange={(value) => setForm({ ...form, variablesJson: value })}
                />
              </Card>
            ) : null}
            {currentStepKey === 'notifications' ? (
              <Card size="small" title={`步骤 ${stepItems.findIndex((item) => item.key === 'notifications') + 1} · 通知配置`}>
                <Form layout="vertical">
                  <Form.Item label="通知配置">
                    {notifications.filter((item) => item.enabled).length === 0 ? (
                      <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-500">
                        还没有可用通知配置，请先到“通知管理”里添加。
                      </div>
                    ) : (
                      <Select
                        mode="multiple"
                        allowClear
                        value={form.notificationIds}
                        placeholder="选择这条流水线要绑定的通知配置"
                        options={notifications
                          .filter((item) => item.enabled)
                          .map((item) => ({
                            label: (
                              <div className="flex items-center gap-2 py-0.5">
                                <span className="font-medium text-slate-800">{item.name}</span>
                                <span
                                  className="rounded-full px-2 py-0.5 text-xs font-medium text-white"
                                  style={{ backgroundColor: '#0f766e' }}
                                >
                                  {item.type === 'FEISHU' ? '飞书' : item.type}
                                </span>
                                <span
                                  className="rounded-full px-2 py-0.5 text-xs font-medium text-white"
                                  style={{ backgroundColor: item.eventType === 'DEPLOYMENT_STARTED' ? '#c2410c' : '#1d4ed8' }}
                                >
                                  {item.eventType === 'DEPLOYMENT_STARTED' ? '开始通知' : '结束通知'}
                                </span>
                              </div>
                            ),
                            value: item.id,
                          }))}
                        onChange={(value) => setForm({ ...form, notificationIds: value })}
                      />
                    )}
                  </Form.Item>
                  {form.notificationIds.length > 0 ? (
                    <div className="space-y-3">
                      {notifications
                        .filter((item) => form.notificationIds.includes(item.id))
                        .map((item) => (
                          <div key={item.id} className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                            <div className="mb-2 text-sm font-medium text-slate-800">{item.name}</div>
                            <div className="mb-2 text-xs text-slate-500">
                              通知渠道类型：{item.type === 'FEISHU' ? '飞书' : item.type}
                              {item.eventType ? ` · 通知类型：${item.eventType === 'DEPLOYMENT_STARTED' ? '开始通知' : '结束通知'}` : ''}
                            </div>
                            <div className="text-xs text-slate-500">
                              绑定后会按这条通知配置自己的通知类型自动发送。
                            </div>
                            {item.description ? <div className="text-xs text-slate-500">{item.description}</div> : null}
                          </div>
                        ))}
                    </div>
                  ) : null}
                </Form>
              </Card>
            ) : null}
          </div>
        </div>
      </Modal>
    </>
  );
}
