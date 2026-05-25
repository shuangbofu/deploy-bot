import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Divider, Form, Input, Modal, Popconfirm, Select, Space, Steps, Switch, Table, Tag, Tabs, message } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { deploymentPluginsApi } from '../../api/deploymentPlugins';
import { templatesApi } from '../../api/templates';
import type { TemplatePayload } from '../../api/types';
import BooleanBadge from '../../components/BooleanBadge';
import EmptyPane from '../../components/EmptyPane';
import CodeEditor from '../../components/CodeEditor';
import PageHeaderBar from '../../components/PageHeaderBar';
import PipelineIcon from '../../components/PipelineIcon';
import ShellVariableHint from '../../components/ShellVariableHint';
import TemplatePreviewStage from '../../components/TemplatePreviewStage';
import TemplateVariablesEditor from '../../components/TemplateVariablesEditor';
import type { DeploymentPluginDefinitionSummary, PluginValueReferenceSummary, PluginVariableMutationRuleSummary, ShellVariableSummary } from '../../types/domain';
import { PHASE_LABEL_MAP, sortByPhase } from '../../utils/tagColors';

interface TemplateFormState {
  name: string;
  description: string;
  pluginId?: string;
  templateType: string;
  variablesSchema: Array<{
    name: string;
    label?: string;
    placeholder?: string;
    required?: boolean;
    pipelineInput?: boolean;
    phase?: 'build' | 'deploy' | 'shared';
    mutationRuleIds?: string[];
  }>;
  buildScriptContent: string;
  deployScriptContent: string;
  monitorProcess: boolean;
}

const phaseClassName = (phase?: string | null) => `app-phase-tag--${phase || 'shared'}`;

const emptyTemplate: TemplateFormState = {
  name: '',
  description: '',
  pluginId: undefined,
  templateType: 'generic',
  variablesSchema: [],
  buildScriptContent: 'set -e\n',
  deployScriptContent: '',
  monitorProcess: false,
};

const buildTemplateFormSnapshot = (value: TemplateFormState) => JSON.stringify(value);

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

const buildTemplateVariablePhaseMap = (variables) => Object.fromEntries(
  (variables || []).map((item) => [item.name, item.phase || 'shared']),
);

const commandSlotLabelMap = {
  BUILD_COMMAND: '主构建命令',
  BACKEND_BUILD_COMMAND: '后端构建命令',
  START_COMMAND: '启动命令',
} as const;

const mutationTypeLabelMap = {
  VARIABLE_INJECT: '变量生成规则',
  COMMAND_REWRITE: '命令参数注入规则',
  COMPOSITE_REUSE: '子单元复用规则',
} as const;

function renderReferences(references?: PluginValueReferenceSummary[]) {
  if (!references?.length) {
    return '-';
  }
  return references.map((item) => {
    if (item.scope === 'COMMAND_SLOT') {
      return `命令：${commandSlotLabelMap[item.key] || item.key}`;
    }
    if (item.scope === 'CONTEXT') {
      return `上下文：${item.label || item.key}`;
    }
    return `变量：${item.label || item.key}`;
  }).join('、');
}

function matchesVariableReference(variable: any, reference?: PluginValueReferenceSummary) {
  if (!variable || !reference) {
    return false;
  }
  if (reference.scope === 'VARIABLE' && reference.key === variable.name) {
    return true;
  }
  return Boolean(variable.binding && reference.scope === variable.binding.scope && reference.key === variable.binding.key);
}

function renderRuleSentence(rule: PluginVariableMutationRuleSummary, variable?: any) {
  const sourceText = renderReferences(rule.sourceReferences);
  const matchedTargets = variable
    ? (rule.targetReferences || []).filter((item) => matchesVariableReference(variable, item))
    : rule.targetReferences;
  const matchedGenerated = variable
    ? (rule.generatedReferences || []).filter((item) => matchesVariableReference(variable, item))
    : rule.generatedReferences;
  const targetText = matchedTargets?.length
    ? renderReferences(matchedTargets)
    : (matchedGenerated?.length ? renderReferences(matchedGenerated) : '当前变量');
  const extraGenerated = !variable && rule.generatedReferences?.length && rule.targetReferences?.length
    ? `，并额外生成 ${renderReferences(rule.generatedReferences)}`
    : '';
  return `把 ${sourceText} 用 ${mutationTypeLabelMap[rule.mutationType] || rule.mutationType} 写到 ${targetText}${extraGenerated}`;
}

function renderRuleShortLabel(rule: PluginVariableMutationRuleSummary) {
  const target = rule.targetReferences?.[0] || rule.generatedReferences?.[0];
  const targetLabel = target
    ? (target.scope === 'COMMAND_SLOT'
      ? (commandSlotLabelMap[target.key] || target.key)
      : (target.label || target.key))
    : '当前变量';
  return `${rule.phase === 'BUILD' ? '构建' : '发布'} · ${targetLabel}`;
}

function renderVariableTitle(item: { label?: string; name: string }) {
  if (item.label && item.label !== item.name) {
    return `${item.label}（${item.name}）`;
  }
  return item.label || item.name;
}

type TemplatePageMode = 'list' | 'create' | 'edit' | 'view' | 'builtin-view';

export default function TemplateAdminPage({ mode = 'list' }: { mode?: TemplatePageMode }) {
  const navigate = useNavigate();
  const { pluginId: routePluginId, templateId, builtinTemplateKey } = useParams();
  const [templates, setTemplates] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [plugins, setPlugins] = useState<DeploymentPluginDefinitionSummary[]>([]);
  const [shellVariables, setShellVariables] = useState<ShellVariableSummary[]>([]);
  const [form, setForm] = useState<TemplateFormState>(emptyTemplate);
  const initialFormSnapshotRef = useRef(buildTemplateFormSnapshot(emptyTemplate));
  const [editingId, setEditingId] = useState();
  const [currentStep, setCurrentStep] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [templateTypeFilter, setTemplateTypeFilter] = useState<string>();
  const [monitorFilter, setMonitorFilter] = useState<string>();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  const updateFormWithSnapshot = (nextForm: TemplateFormState) => {
    initialFormSnapshotRef.current = buildTemplateFormSnapshot(nextForm);
    setForm(nextForm);
  };

  const isFormChanged = () => initialFormSnapshotRef.current !== buildTemplateFormSnapshot(form);

  const cancelEditing = () => {
    if (!isFormChanged()) {
      navigate(`/admin/plugins/${routePluginId}/templates`);
      return;
    }
    Modal.confirm({
      title: '丢弃当前修改？',
      content: '当前模板配置还没有保存，确认取消后这些修改会丢失。',
      okText: '丢弃修改',
      cancelText: '继续编辑',
      okButtonProps: { danger: true },
      onOk: () => navigate(`/admin/plugins/${routePluginId}/templates`),
    });
  };

  const loadTemplates = async () => {
    setLoading(true);
    try {
      const result = await templatesApi.listPage({
        page: pagination.current,
        pageSize: pagination.pageSize,
        keyword: keyword.trim() || undefined,
        pluginId: routePluginId,
        templateType: templateTypeFilter,
        monitorProcess: monitorFilter == null ? undefined : monitorFilter === 'true',
      });
      setTemplates(result.items);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTemplates().catch(() => message.error('加载模板失败'));
  }, [pagination.current, pagination.pageSize, keyword, routePluginId, templateTypeFilter, monitorFilter]);

  useEffect(() => {
    Promise.all([deploymentPluginsApi.list(), deploymentPluginsApi.shellVariables()])
      .then(([pluginResult, shellVariableResult]) => {
        setPlugins(pluginResult);
        setShellVariables(shellVariableResult);
      })
      .catch(() => message.error('加载插件失败'));
  }, []);

  const pluginNameMap = useMemo(() => {
    const map = new Map<string, string>();
    plugins.forEach((item) => map.set(item.descriptor.pluginId, item.descriptor.displayName));
    return map;
  }, [plugins]);

  const activePlugin = useMemo(
    () => plugins.find((item) => item.descriptor.pluginId === routePluginId) || null,
    [plugins, routePluginId],
  );

  const activePluginName = routePluginId ? pluginNameMap.get(routePluginId) : undefined;
  const activePluginMutationRules = useMemo(
    () => activePlugin?.variableMutationRules || [],
    [activePlugin],
  );
  const builtinTemplates = useMemo(
    () => (activePlugin?.builtinTemplates || []).map((item, index) => ({
      ...item,
      key: `${activePlugin?.descriptor.pluginId || 'plugin'}-${item.templateKey || item.name || index}`,
    })),
    [activePlugin],
  );
  const selectedBuiltinTemplate = useMemo(
    () => builtinTemplates.find((item) => (item.templateKey || item.key) === builtinTemplateKey) || null,
    [builtinTemplateKey, builtinTemplates],
  );

  useEffect(() => {
    if (!routePluginId) {
      navigate('/admin/plugins', { replace: true });
    }
  }, [navigate, routePluginId]);

  useEffect(() => {
    if (!activePlugin || mode !== 'create') {
      return;
    }
    const builtin = activePlugin.builtinTemplates?.[0];
    setEditingId(undefined);
    updateFormWithSnapshot({
      name: '',
      description: builtin?.description || '',
      pluginId: activePlugin.descriptor.pluginId,
      templateType: builtin?.templateType || activePlugin.descriptor.templateTypes?.[0] || 'generic',
      variablesSchema: parseVariablesSchema(builtin?.variablesSchema),
      buildScriptContent: builtin?.buildScriptContent || 'set -e\n',
      deployScriptContent: builtin?.deployScriptContent || '',
      monitorProcess: Boolean(builtin?.monitorProcess),
    });
    setCurrentStep(0);
  }, [activePlugin, mode]);

  useEffect(() => {
    if (mode !== 'edit' && mode !== 'view' || !templateId) {
      return;
    }
    templatesApi.get(Number(templateId))
      .then((record) => {
        setEditingId(mode === 'edit' ? record.id : undefined);
        updateFormWithSnapshot({
          name: record.name || '',
          description: record.description || '',
          pluginId: record.pluginId || undefined,
          templateType: record.templateType || 'generic',
          variablesSchema: parseVariablesSchema(record.variablesSchema),
          buildScriptContent: record.buildScriptContent || 'set -e\n',
          deployScriptContent: record.deployScriptContent || '',
          monitorProcess: Boolean(record.monitorProcess),
        });
        setCurrentStep(0);
      })
      .catch(() => message.error('加载模板详情失败'));
  }, [mode, templateId]);

  const openCreate = () => {
    navigate(`/admin/plugins/${routePluginId}/templates/new`);
  };

  const openEdit = (record) => {
    navigate(`/admin/plugins/${routePluginId}/templates/${record.id}/edit`);
  };

  const saveTemplate = async () => {
    const normalizedVariables = form.variablesSchema
      .map((item) => ({
        name: (item.name || '').trim(),
        label: (item.label || '').trim(),
        placeholder: (item.placeholder || '').trim(),
        required: Boolean(item.required),
        pipelineInput: item.pipelineInput !== false,
        phase: item.phase || 'shared',
        mutationRuleIds: Array.isArray(item.mutationRuleIds) ? item.mutationRuleIds.filter(Boolean) : [],
      }))
      .filter((item) => item.name);

    const payload: TemplatePayload = {
      ...form,
      pluginId: form.pluginId,
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
    navigate(`/admin/plugins/${routePluginId}/templates`);
    message.success(editingId ? '模板已更新' : '模板已创建');
  };

  const removeTemplate = async (id) => {
    await templatesApi.remove(id);
    await loadTemplates();
    message.success('模板已删除');
  };

  const syncVariablesFromScript = (phase) => {
    const reservedVariables = new Set(shellVariables.map((item) => item.contextKey).filter(Boolean));
    const buildNames = extractVariableNames(form.buildScriptContent).filter((name) => !reservedVariables.has(name));
    const deployNames = extractVariableNames(form.deployScriptContent).filter((name) => !reservedVariables.has(name));
    const targetNames = phase === 'build' ? buildNames : deployNames;
    const currentMap = new Map((form.variablesSchema || []).map((item) => [item.name, item]));
    const pluginVariableMap = new Map((activePlugin?.variableDefinitions || []).map((item) => [item.key, item]));

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
      const pluginDefinition = pluginVariableMap.get(name);
      nextMap.set(name, {
        ...(currentMap.get(name) || {
          name,
          label: '',
          placeholder: '',
          required: false,
          pipelineInput: pluginDefinition?.pipelineInput !== false,
          mutationRuleIds: [],
        }),
        phase: resolvedPhase,
        pipelineInput: currentMap.get(name)?.pipelineInput ?? (pluginDefinition?.pipelineInput !== false),
        mutationRuleIds: currentMap.get(name)?.mutationRuleIds?.length ? currentMap.get(name)?.mutationRuleIds : (pluginDefinition?.mutationRuleIds || []),
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
  const templateVariablePhases = useMemo(
    () => buildTemplateVariablePhaseMap(form.variablesSchema),
    [form.variablesSchema],
  );

  const tableData = useMemo(() => templates.map((item) => ({
    ...item,
    parsedVariables: parseVariablesSchema(item.variablesSchema),
  })), [templates]);

  const mutationRuleOptions = useMemo(
    () => activePluginMutationRules.map((rule, index) => ({
      value: rule.ruleId || `${rule.phase}-${rule.mutationType}-${index}`,
      label: renderRuleShortLabel(rule),
    })),
    [activePluginMutationRules],
  );
  const mutationRuleHelpMap = useMemo(
    () => Object.fromEntries(activePluginMutationRules.map((rule, index) => [
      rule.ruleId || `${rule.phase}-${rule.mutationType}-${index}`,
      renderRuleSentence(rule),
    ])),
    [activePluginMutationRules],
  );

  const renderVariableLogic = (item) => {
    const rules = (item.mutationRuleIds || [])
      .map((ruleId) => activePluginMutationRules.find((rule) => rule.ruleId === ruleId))
      .filter(Boolean);
    if (!rules.length) {
      return <span className="text-slate-400">无</span>;
    }
    return (
      <div className="space-y-1">
        {rules.map((rule) => (
          <div key={rule.ruleId} className="text-xs leading-5 text-slate-600">
            {renderRuleSentence(rule, item)}
          </div>
        ))}
      </div>
    );
  };

  const renderPreviewVariableCards = (variables, phase: 'build' | 'deploy') => {
    const phaseVariables = sortByPhase(variables.filter((item) => {
      const itemPhase = item.phase || 'shared';
      return itemPhase === phase || itemPhase === 'shared';
    }));
    if (!phaseVariables.length) {
      return <div className="rounded-lg border border-dashed border-slate-200 px-3 py-2 text-xs text-slate-500">这个默认模板没有{phase === 'build' ? '构建' : '发布'}变量定义。</div>;
    }
    return (
      <div className="max-h-56 overflow-y-auto rounded-lg border border-slate-200">
        {phaseVariables.map((item) => (
          <div key={`${phase}-${item.name}`} className="grid grid-cols-[minmax(260px,1fr)_minmax(260px,2fr)] items-start gap-3 border-b border-slate-100 px-3 py-2 text-sm last:border-b-0">
            <span className="whitespace-nowrap font-medium text-slate-800">
              {renderVariableTitle(item)}
              {item.required ? ' *' : ''}
            </span>
            <div>{renderVariableLogic(item)}</div>
          </div>
        ))}
      </div>
    );
  };

  const renderTemplateFormContent = () => (
    <div className="mx-auto flex h-full min-h-0 max-w-5xl flex-col">
      <div className="sticky top-0 z-20 pb-3">
        <Card className="app-card">
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
      </div>
      <div className="min-h-0 flex-1 overflow-auto pb-4">
        {currentStep === 0 ? (
          <Card title="基础信息" className="app-card">
            <Form layout="vertical">
              <Form.Item label="模板名称">
                <Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
              </Form.Item>
              <Form.Item label="归属插件">
                <Input value={activePluginName || form.pluginId || ''} disabled />
              </Form.Item>
              <Form.Item label="描述">
                <Input.TextArea rows={3} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
              </Form.Item>
              <Form.Item label="插件内类型">
                <Select
                  value={form.templateType}
                  options={availableTemplateTypeOptions}
                  onChange={(value) => setForm({ ...form, pluginId: routePluginId, templateType: value })}
                  disabled={availableTemplateTypeOptions.length <= 1}
                />
              </Form.Item>
            </Form>
          </Card>
        ) : null}
        {currentStep === 1 ? (
          <Card title="构建" className="app-card">
            <div className="mb-2 font-medium text-slate-800">本机构建脚本</div>
            <ShellVariableHint stage="build" scriptContent={form.buildScriptContent} variables={shellVariables} />
            <CodeEditor
              rows={14}
              language="shell"
              value={form.buildScriptContent}
              onChange={(value) => setForm({ ...form, buildScriptContent: value })}
              templateVariablePhases={templateVariablePhases}
            />
            <div className="mt-4">
              <TemplateVariablesEditor
                title="构建变量"
                phase="build"
                availableMutationRules={mutationRuleOptions}
                mutationRuleHelpMap={mutationRuleHelpMap}
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
          </Card>
        ) : null}
        {currentStep === 2 ? (
          <Card title="发布" className="app-card">
            <div className="mb-2 font-medium text-slate-800">目标主机发布脚本</div>
            <ShellVariableHint stage="deploy" scriptContent={form.deployScriptContent} variables={shellVariables} />
            <CodeEditor
              rows={10}
              language="shell"
              value={form.deployScriptContent}
              onChange={(value) => setForm({ ...form, deployScriptContent: value })}
              templateVariablePhases={templateVariablePhases}
            />
            <div className="mt-4">
              <TemplateVariablesEditor
                title="发布变量"
                phase="deploy"
                availableMutationRules={mutationRuleOptions}
                mutationRuleHelpMap={mutationRuleHelpMap}
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
            <Divider />
            <Form layout="vertical">
              <Form.Item label="监控进程">
                <Switch
                  checked={Boolean(form.monitorProcess)}
                  checkedChildren="是"
                  unCheckedChildren="否"
                  onChange={(checked) => setForm({ ...form, monitorProcess: checked })}
                />
              </Form.Item>
            </Form>
          </Card>
        ) : null}
        {currentStep === 3 ? (
          <Card title="变量总览" className="app-card">
            <TemplateVariablesEditor
              availableMutationRules={mutationRuleOptions}
              mutationRuleHelpMap={mutationRuleHelpMap}
              value={form.variablesSchema}
              onChange={(value) => setForm({ ...form, variablesSchema: value })}
            />
          </Card>
        ) : null}
      </div>
      <div className="shrink-0 flex justify-end gap-2 border-t border-slate-200/70 px-4 py-3 backdrop-blur-sm">
        {currentStep > 0 ? <Button onClick={() => setCurrentStep((value) => value - 1)}>上一步</Button> : null}
        {currentStep < 3 ? <Button onClick={() => setCurrentStep((value) => value + 1)}>下一步</Button> : null}
      </div>
    </div>
  );

  const renderTemplatePreviewContent = (template: any) => (
    <div className="space-y-4">
      <Card className="app-card template-detail-summary">
        <div className="template-detail-summary__title">
          <div className="flex min-w-0 items-center gap-3">
            <PipelineIcon type={template.templateType} />
            <div className="min-w-0">
              <div className="truncate text-base font-semibold text-slate-900">{template.name || '未命名模板'}</div>
              <div className="mt-1 text-sm leading-6 text-slate-500">{template.description || '暂无说明'}</div>
            </div>
          </div>
          <span className={`template-detail-monitor ${template.monitorProcess ? 'template-detail-monitor--on' : ''}`}>
            <span />
            {template.monitorProcess ? '监控进程' : '不监控进程'}
          </span>
        </div>
        <div className="template-detail-meta">
          <div>
            <span>归属插件</span>
            <strong>{activePluginName || template.pluginId || '-'}</strong>
          </div>
          <div>
            <span>插件内类型</span>
            <strong>{template.templateType ? template.templateType.replace(/_/g, ' / ') : '通用'}</strong>
          </div>
          <div>
            <span>变量数量</span>
            <strong>{parseVariablesSchema(template.variablesSchema).length} 个</strong>
          </div>
          <div>
            <span>发布阶段</span>
            <strong>{template.deployScriptContent ? '有独立发布脚本' : '无独立发布脚本'}</strong>
          </div>
        </div>
      </Card>
      <Card className="app-card template-detail-content-card">
      <Tabs
        className="app-soft-tabs"
        items={[
          {
            key: 'build',
            label: '构建',
            children: (
              <TemplatePreviewStage
                stage="build"
                variables={renderPreviewVariableCards(parseVariablesSchema(template.variablesSchema), 'build')}
                scriptContent={template.buildScriptContent}
                templateVariablePhases={buildTemplateVariablePhaseMap(parseVariablesSchema(template.variablesSchema))}
                shellVariables={shellVariables}
              />
            ),
          },
          {
            key: 'deploy',
            label: '发布',
            children: (
              <TemplatePreviewStage
                stage="deploy"
                variables={renderPreviewVariableCards(parseVariablesSchema(template.variablesSchema), 'deploy')}
                scriptContent={template.deployScriptContent}
                templateVariablePhases={buildTemplateVariablePhaseMap(parseVariablesSchema(template.variablesSchema))}
                shellVariables={shellVariables}
              />
            ),
          },
        ]}
      />
      </Card>
    </div>
  );

  const availableTemplateTypeOptions = useMemo(() => {
    const types = activePlugin?.descriptor.templateTypes || [];
    if (types.length === 0) {
      return [{ label: '通用', value: 'generic' }];
    }
    return types.map((item) => ({
      label: item.replace(/_/g, ' / '),
      value: item,
    }));
  }, [activePlugin]);

  useEffect(() => {
    if (mode !== 'create' && mode !== 'edit') {
      return;
    }
    const allowed = new Set(availableTemplateTypeOptions.map((item) => item.value));
    if (!allowed.has(form.templateType)) {
      setForm((previous) => ({
        ...previous,
        pluginId: routePluginId,
        templateType: availableTemplateTypeOptions[0]?.value || 'generic',
      }));
    }
  }, [availableTemplateTypeOptions, form.templateType, mode, routePluginId]);

  if (mode === 'create' || mode === 'edit') {
    return (
      <>
        <PageHeaderBar
          title={mode === 'edit' ? '编辑模板' : '新建模板'}
          description={activePluginName || '插件模板'}
          extra={(
            <Space>
              <Button danger={isFormChanged()} onClick={cancelEditing}>{isFormChanged() ? '取消' : '返回'}</Button>
              <Button onClick={() => updateFormWithSnapshot(JSON.parse(initialFormSnapshotRef.current))}>重置</Button>
              <Button type="primary" onClick={() => saveTemplate().catch(() => message.error(mode === 'edit' ? '更新模板失败' : '创建模板失败'))}>
                {mode === 'edit' ? '保存' : '创建'}
              </Button>
            </Space>
          )}
        />
        <div className="app-page-scroll min-h-0">
          {renderTemplateFormContent()}
        </div>
      </>
    );
  }

  if (mode === 'view') {
    return (
      <>
        <PageHeaderBar
          title={form.name || '查看模板'}
          description={form.description || activePluginName || '插件模板'}
          extra={(
            <Space>
              <Button onClick={() => navigate(`/admin/plugins/${routePluginId}/templates`)}>返回模板</Button>
              <Button type="primary" onClick={() => navigate(`/admin/plugins/${routePluginId}/templates/${templateId}/edit`)}>编辑</Button>
            </Space>
          )}
        />
        <div className="app-page-scroll">
          <div className="mx-auto max-w-6xl">
            {renderTemplatePreviewContent({
              ...form,
              variablesSchema: form.variablesSchema,
            })}
          </div>
        </div>
      </>
    );
  }

  if (mode === 'builtin-view') {
    return (
      <>
        <PageHeaderBar
          title={selectedBuiltinTemplate?.name || '默认模板'}
          description={selectedBuiltinTemplate?.description || activePluginName || '插件模板'}
          extra={<Button onClick={() => navigate(`/admin/plugins/${routePluginId}/templates`)}>返回模板</Button>}
        />
        <div className="app-page-scroll">
          <div className="mx-auto max-w-6xl">
            {selectedBuiltinTemplate ? renderTemplatePreviewContent(selectedBuiltinTemplate) : (
              <Card className="app-card">
                <EmptyPane description="没有找到这个默认模板。" />
              </Card>
            )}
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeaderBar
        title={activePluginName ? `${activePluginName} · 模板` : '插件模板'}
        description={activePluginName ? `维护插件「${activePluginName}」的默认模板和自定义模板。` : '查看插件模板。'}
        extra={(
          <Space>
            <Button onClick={() => navigate('/admin/plugins')}>返回插件</Button>
            <Button onClick={() => loadTemplates().catch(() => message.error('加载模板失败'))}>刷新</Button>
            <Button type="primary" onClick={openCreate}>新建模板</Button>
          </Space>
        )}
      />
      <div className="app-page-scroll">
      <Card className="app-card">
        <Tabs
          className="app-soft-tabs"
          defaultActiveKey="builtin"
          items={[
            {
              key: 'builtin',
              label: '默认模板',
              children: (
                <div className="space-y-3">
                  <div className="rounded-2xl bg-slate-50 px-4 py-3 text-sm text-slate-600">
                    这里展示的是插件 resources 里自带的只读默认模板，不能直接编辑；如果同一个插件后面真有多种默认模板，就按“适用类型”并列展示。
                  </div>
                  <Table
                    rowKey="key"
                    pagination={false}
                    dataSource={builtinTemplates}
                    locale={{ emptyText: <EmptyPane description="当前插件没有自带默认模板。" /> }}
                    columns={[
                      { title: '模板名称', dataIndex: 'name', width: 220 },
                      {
                        title: '适用类型',
                        width: 160,
                        render: (_, record) => (
                          <div className="flex items-center gap-2">
                            <PipelineIcon type={record.templateType} />
                            <span>{record.templateType ? record.templateType.replace(/_/g, ' / ') : '通用'}</span>
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
                        title: '查看内容',
                        width: 120,
                        render: (_, record) => (
                          <Button size="small" onClick={() => navigate(`/admin/plugins/${routePluginId}/templates/builtin/${record.templateKey || record.key}`)}>
                            查看详情
                          </Button>
                        ),
                      },
                    ]}
                  />
                </div>
              ),
            },
            {
              key: 'derived',
              label: `派生模板（${total}）`,
              children: (
                <>
        <div className="app-filter-grid">
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
            placeholder="筛选插件内类型"
            options={availableTemplateTypeOptions}
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
          dataSource={tableData}
          locale={{ emptyText: <EmptyPane description="还没有模板，点击右上角先沉淀一套真实部署脚本。" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (current, pageSize) => setPagination({ current, pageSize }),
          }}
          columns={[
              { title: '名称', dataIndex: 'name', width: 180 },
              {
                title: '插件内类型',
                width: 140,
                render: (_, record) => (
                  <span>{record.templateType ? record.templateType.replace(/_/g, ' / ') : '通用'}</span>
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
                        className={`app-phase-tag ${phaseClassName(item.phase)} !border-0`}
                      >
                        [{PHASE_LABEL_MAP[item.phase || 'shared'] || '共用'}] {renderVariableTitle(item)}
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
                width: 170,
                render: (_, record) => (
                  <Space>
                    <Button size="small" onClick={() => navigate(`/admin/plugins/${routePluginId}/templates/${record.id}`)}>查看</Button>
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
                </>
              ),
            },
          ]}
        />
      </Card>
      </div>
    </>
  );
}
