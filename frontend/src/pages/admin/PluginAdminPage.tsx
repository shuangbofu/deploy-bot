import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Descriptions, Modal, Tag, Tabs, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { deploymentPluginsApi } from '../../api/deploymentPlugins';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import PipelineIcon from '../../components/PipelineIcon';
import TemplatePreviewStage from '../../components/TemplatePreviewStage';
import type { DeploymentPluginDefinitionSummary, ShellVariableSummary } from '../../types/domain';
import { getRuntimeEnvironmentTypeLabel } from '../../utils/runtimeEnvironment';
import { sortByPhase } from '../../utils/tagColors';

const commandSlotLabelMap: Record<string, string> = {
  BUILD_COMMAND: '主构建命令',
  BACKEND_BUILD_COMMAND: '后端构建命令',
  START_COMMAND: '启动命令',
};

const fieldKindLabelMap: Record<string, string> = {
  TEXT: '文本',
  TEXTAREA: '代码',
};

const yesTagStyle = { backgroundColor: '#15803d', color: '#fff', borderColor: 'transparent' };
const noTagStyle = { backgroundColor: '#cbd5e1', color: '#334155', borderColor: 'transparent' };

function resolveTemplateTypeLabel(templateType?: string | null) {
  return templateType ? templateType.replace(/_/g, ' / ') : '通用';
}

function parseVariablesSchema(content?: string | null) {
  if (!content) {
    return [];
  }
  try {
    const parsed = JSON.parse(content);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

const buildTemplateVariablePhaseMap = (variables: any[]) => Object.fromEntries(
  (variables || []).map((item: any) => [item.name, item.phase || 'shared']),
);

function matchesVariableReference(variable: any, reference?: any) {
  if (!variable || !reference) {
    return false;
  }
  if (reference.scope === 'VARIABLE' && reference.key === variable.name) {
    return true;
  }
  return Boolean(variable.binding && reference.scope === variable.binding.scope && reference.key === variable.binding.key);
}

function renderRuleSentence(rule: any, variable?: any) {
  const sourceText = (rule.sourceReferences || []).map((item) => {
    if (item.scope === 'COMMAND_SLOT') {
      return `命令：${commandSlotLabelMap[item.key] || item.key}`;
    }
    if (item.scope === 'CONTEXT') {
      return `上下文：${item.label || item.key}`;
    }
    return `变量：${item.label || item.key}`;
  }).join('、');
  const matchedTargets = variable
    ? (rule.targetReferences || []).filter((item: any) => matchesVariableReference(variable, item))
    : rule.targetReferences || [];
  const matchedGenerated = variable
    ? (rule.generatedReferences || []).filter((item: any) => matchesVariableReference(variable, item))
    : rule.generatedReferences || [];
  const targetText = matchedTargets.length
    ? matchedTargets.map((item) => item.scope === 'COMMAND_SLOT' ? `命令：${commandSlotLabelMap[item.key] || item.key}` : `变量：${item.label || item.key}`).join('、')
    : (matchedGenerated.length
      ? matchedGenerated.map((item) => `变量：${item.label || item.key}`).join('、')
      : '当前变量');
  const methodLabel = rule.mutationType === 'COMMAND_REWRITE'
    ? '命令参数注入规则'
    : rule.mutationType === 'VARIABLE_INJECT'
      ? '变量生成规则'
      : '子单元复用规则';
  const extraGenerated = !variable && (rule.generatedReferences || []).length && (rule.targetReferences || []).length
    ? `，并额外生成 ${(rule.generatedReferences || []).map((item) => `变量：${item.label || item.key}`).join('、')}`
    : '';
  return `把 ${sourceText} 用 ${methodLabel} 写到 ${targetText}${extraGenerated}`;
}

function renderVariableTitle(item: { label?: string; name: string }) {
  if (item.label && item.label !== item.name) {
    return `${item.label}（${item.name}）`;
  }
  return item.label || item.name;
}

export default function PluginAdminPage() {
  const navigate = useNavigate();
  const [plugins, setPlugins] = useState<DeploymentPluginDefinitionSummary[]>([]);
  const [shellVariables, setShellVariables] = useState<ShellVariableSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [activePlugin, setActivePlugin] = useState<DeploymentPluginDefinitionSummary | null>(null);
  const [previewBuiltinTemplate, setPreviewBuiltinTemplate] = useState<any | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const [pluginResult, shellVariableResult] = await Promise.all([
        deploymentPluginsApi.list(),
        deploymentPluginsApi.shellVariables(),
      ]);
      setPlugins(pluginResult);
      setShellVariables(shellVariableResult);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData().catch(() => message.error('加载插件失败'));
  }, []);

  const tableData = useMemo(() => plugins.map((item) => ({
    ...item,
    key: item.descriptor.pluginId,
  })), [plugins]);

  const builtinTemplates = useMemo(
    () => (activePlugin?.builtinTemplates || []).map((item, index) => ({
      ...item,
      key: `${activePlugin?.descriptor.pluginId || 'plugin'}-${item.templateKey || item.name || index}`,
    })),
    [activePlugin],
  );
  const activeRuleMap = useMemo(
    () => new Map((activePlugin?.variableMutationRules || []).map((item: any) => [item.ruleId || '', item])),
    [activePlugin],
  );
  const renderPreviewVariableCards = (variables: any[], phase: 'build' | 'deploy') => {
    const phaseVariables = sortByPhase(variables.filter((item) => {
      const itemPhase = item.phase || 'shared';
      return itemPhase === phase || itemPhase === 'shared';
    }));
    if (!phaseVariables.length) {
      return <div className="rounded-lg border border-dashed border-slate-200 px-3 py-2 text-xs text-slate-500">这个默认模板没有{phase === 'build' ? '构建' : '发布'}变量定义。</div>;
    }
    return (
      <div className="max-h-56 overflow-y-auto rounded-lg border border-slate-200">
        {phaseVariables.map((item: any) => {
          const rules = (item.mutationRuleIds || []).map((ruleId: string) => activeRuleMap.get(ruleId)).filter(Boolean);
          return (
            <div key={`${phase}-${item.name}`} className="grid grid-cols-[minmax(260px,1fr)_minmax(260px,2fr)] items-start gap-3 border-b border-slate-100 px-3 py-2 text-sm last:border-b-0">
              <span className="whitespace-nowrap font-medium text-slate-800">
                {renderVariableTitle(item)}
                {item.required ? ' *' : ''}
              </span>
              <div className="space-y-1">
                {rules.length ? rules.map((rule: any, index: number) => (
                  <div key={`${rule.ruleId || 'rule'}-${index}`} className="text-xs leading-5 text-slate-600">
                    {renderRuleSentence(rule, item)}
                  </div>
                )) : <span className="text-xs text-slate-400">无</span>}
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <>
      <PageHeaderBar
        title="插件管理"
        description="查看当前后端真正加载到的部署类型插件，以及它们自带模板、组件依赖和配置能力。"
        extra={<Button onClick={() => loadData().catch(() => message.error('加载插件失败'))}>刷新</Button>}
      />
      <div className="app-page-scroll">
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2 2xl:grid-cols-3">
          {loading ? (
            Array.from({ length: 3 }).map((_, index) => (
              <Card key={`plugin-skeleton-${index}`} loading className="app-card" />
            ))
          ) : null}
          {!loading && !tableData.length ? (
            <Card className="app-card xl:col-span-2 2xl:col-span-3">
              <EmptyPane description="当前没有发现任何部署类型插件，请先确认插件 starter 或外部插件 jar 是否已被引入。" />
            </Card>
          ) : null}
          {!loading ? tableData.map((row) => (
            <Card
              key={row.key}
              className="app-card border-slate-200"
              bodyStyle={{ padding: 18 }}
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex min-w-0 items-start gap-3">
                  <div className="rounded-2xl bg-slate-100 p-3 text-slate-700">
                    <PipelineIcon type={row.descriptor.templateTypes?.[0]} />
                  </div>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <div className="truncate text-base font-semibold text-slate-900">
                        {row.descriptor.displayName}
                      </div>
                      <Tag style={yesTagStyle} className="!m-0 !border-0">
                        已安装
                      </Tag>
                      {row.descriptor.builtin ? (
                        <Tag style={{ backgroundColor: '#0f766e', color: '#fff', borderColor: 'transparent' }} className="!m-0 !border-0">
                          系统插件
                        </Tag>
                      ) : null}
                      {row.descriptor.composite ? (
                        <Tag style={{ backgroundColor: '#1d4ed8', color: '#fff', borderColor: 'transparent' }} className="!m-0 !border-0">
                          复合类型
                        </Tag>
                      ) : null}
                    </div>
                    <div className="mt-1 text-xs text-slate-500">{row.descriptor.pluginId}</div>
                  <div className="mt-2 text-sm text-slate-600">{row.descriptor.description || '当前插件未提供额外描述。'}</div>
                </div>
              </div>
                <Tag style={noTagStyle} className="!m-0 !border-0">
                  {row.descriptor.category}
                </Tag>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-3">
                <div className="rounded-2xl bg-slate-50 px-4 py-3">
                  <div className="text-xs font-medium text-slate-500">构建运行时</div>
                  <div className="mt-1 inline-flex items-center whitespace-nowrap rounded-xl bg-white px-3 py-2 text-sm font-semibold text-slate-800">
                    {(row.runtimeRequirement?.buildRuntimeTypes || []).map((item: string) => getRuntimeEnvironmentTypeLabel(item)).join(' / ') || '无'}
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 px-4 py-3">
                  <div className="text-xs font-medium text-slate-500">目标运行时</div>
                  <div className="mt-1 inline-flex items-center whitespace-nowrap rounded-xl bg-white px-3 py-2 text-sm font-semibold text-slate-800">
                    {(row.runtimeRequirement?.targetRuntimeTypes || []).map((item: string) => getRuntimeEnvironmentTypeLabel(item)).join(' / ') || '无'}
                  </div>
                </div>
                <div className="rounded-2xl bg-slate-50 px-4 py-3">
                  <div className="text-xs font-medium text-slate-500">适用类型</div>
                  <div className="mt-1 text-sm font-semibold text-slate-800">
                    {row.descriptor.templateTypes?.map((item: string) => resolveTemplateTypeLabel(item)).join(' / ') || '通用'}
                  </div>
                </div>
              </div>
              <div className="mt-4 flex justify-end gap-2">
                <Button size="small" onClick={() => setActivePlugin(row)}>
                  查看详情
                </Button>
                <Button size="small" onClick={() => navigate(`/admin/plugins/${encodeURIComponent(row.descriptor.pluginId)}/templates`)}>
                  查看模板
                </Button>
              </div>
            </Card>
          )) : null}
        </div>
      </div>
      <Modal
        title={activePlugin?.descriptor.displayName || '插件详情'}
        open={Boolean(activePlugin)}
        width={1100}
        footer={null}
        onCancel={() => setActivePlugin(null)}
        destroyOnHidden
      >
        {activePlugin ? (
          <div className="max-h-[72vh] overflow-y-auto pr-2">
            <section className="border-b border-slate-100 pb-4">
              <h3 className="mb-3 text-base font-semibold text-slate-900">基础信息</h3>
              <Descriptions size="small" column={2}>
                <Descriptions.Item label="插件标识">{activePlugin.descriptor.pluginId}</Descriptions.Item>
                <Descriptions.Item label="分类">{activePlugin.descriptor.category}</Descriptions.Item>
                <Descriptions.Item label="提供方">{activePlugin.descriptor.provider}</Descriptions.Item>
                <Descriptions.Item label="复合类型">{activePlugin.descriptor.composite ? '是' : '否'}</Descriptions.Item>
                <Descriptions.Item label="说明" span={2}>
                  {activePlugin.descriptor.description || '-'}
                </Descriptions.Item>
              </Descriptions>
            </section>

            <section className="border-b border-slate-100 py-4">
              <h3 className="mb-3 text-base font-semibold text-slate-900">组件环境</h3>
              <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-2">
                <div>
                  <div className="mb-1 text-xs text-slate-500">构建环境依赖</div>
                  <div className="font-medium text-slate-800">
                    {(activePlugin.runtimeRequirement.buildRuntimeTypes || []).map((item) => getRuntimeEnvironmentTypeLabel(item)).join(' / ') || '无'}
                  </div>
                </div>
                <div>
                  <div className="mb-1 text-xs text-slate-500">目标运行依赖</div>
                  <div className="font-medium text-slate-800">
                    {(activePlugin.runtimeRequirement.targetRuntimeTypes || []).map((item) => getRuntimeEnvironmentTypeLabel(item)).join(' / ') || '无'}
                  </div>
                </div>
              </div>
            </section>

            <section className="border-b border-slate-100 py-4">
              <h3 className="mb-3 text-base font-semibold text-slate-900">流水线配置</h3>
              {activePlugin.pipelineFormSchema?.sections?.length ? (
                <div className="space-y-5">
                  {activePlugin.pipelineFormSchema.sections.map((section) => (
                    <div key={section.key}>
                      <div className="mb-1 text-sm font-semibold text-slate-800">{section.title}</div>
                      {section.description ? (
                        <div className="mb-2 text-sm text-slate-500">{section.description}</div>
                      ) : null}
                      <div className="divide-y divide-slate-100 border-y border-slate-100">
                        {section.fields.map((row) => (
                          <div key={`${section.key}-${row.key}`} className="grid grid-cols-1 gap-2 py-3 md:grid-cols-[220px_1fr]">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-sm font-medium text-slate-800">{row.label}</span>
                              <Tag style={row.required ? yesTagStyle : noTagStyle} className="!m-0 !border-0">
                                {row.required ? '必填' : '可选'}
                              </Tag>
                              {fieldKindLabelMap[row.type] ? (
                                <Tag style={{ backgroundColor: '#e2e8f0', color: '#334155', borderColor: 'transparent' }} className="!m-0 !border-0">
                                  {fieldKindLabelMap[row.type]}
                                </Tag>
                              ) : null}
                            </div>
                            <div className="text-sm leading-6 text-slate-600">
                              {row.helpText || row.placeholder || '-'}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="rounded-lg border border-dashed border-slate-200 px-3 py-2 text-xs text-slate-500">
                  当前插件没有额外的流水线配置项。
                </div>
              )}
            </section>

            <section className="pt-4">
              <h3 className="mb-3 text-base font-semibold text-slate-900">默认模板</h3>
              {builtinTemplates.length ? (
                <div className="overflow-hidden rounded-lg border border-slate-200">
                  <div className="grid grid-cols-[minmax(180px,1.1fr)_160px_minmax(220px,2fr)_90px_90px] gap-3 border-b border-slate-100 px-3 py-2 text-xs font-medium text-slate-500">
                    <span>模板名称</span>
                    <span>适用类型</span>
                    <span>描述</span>
                    <span>监控进程</span>
                    <span>查看内容</span>
                  </div>
                  {builtinTemplates.map((row) => (
                    <div key={row.key} className="grid grid-cols-[minmax(180px,1.1fr)_160px_minmax(220px,2fr)_90px_90px] items-center gap-3 border-b border-slate-100 px-3 py-2 text-sm last:border-b-0">
                      <span className="font-medium text-slate-800">{row.name}</span>
                      <div className="flex items-center gap-2 text-slate-700">
                        <PipelineIcon type={row.templateType} />
                        <span>{resolveTemplateTypeLabel(row.templateType)}</span>
                      </div>
                      <span className="truncate text-slate-600" title={row.description || '-'}>
                        {row.description || '-'}
                      </span>
                      <Tag style={row.monitorProcess ? yesTagStyle : noTagStyle} className="!m-0 !border-0 text-center">
                        {row.monitorProcess ? '是' : '否'}
                      </Tag>
                      <Button size="small" onClick={() => setPreviewBuiltinTemplate(row)}>
                        查看详情
                      </Button>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="rounded-lg border border-dashed border-slate-200 px-3 py-2 text-xs text-slate-500">
                  当前插件没有自带默认模板。
                </div>
              )}
            </section>
          </div>
        ) : null}
      </Modal>
      <Modal
        title={previewBuiltinTemplate ? (
          <div className="flex flex-wrap items-center gap-2">
            <Tag color="green" className="!m-0">插件自带</Tag>
            <Tag color="blue" className="!m-0">{previewBuiltinTemplate.templateType ? previewBuiltinTemplate.templateType.replace(/_/g, ' / ') : '通用'}</Tag>
            <span>{previewBuiltinTemplate.name}</span>
            <span className="ml-1 inline-flex items-center gap-1 text-sm font-normal text-slate-600">
              <span className={`h-2 w-2 rounded-full ${previewBuiltinTemplate.monitorProcess ? 'bg-emerald-500' : 'bg-slate-300'}`} />
              {previewBuiltinTemplate.monitorProcess ? '监控进程' : '不监控进程'}
            </span>
          </div>
        ) : '插件自带模板'}
        open={Boolean(previewBuiltinTemplate)}
        width={980}
        footer={null}
        onCancel={() => setPreviewBuiltinTemplate(null)}
        destroyOnHidden
      >
        {previewBuiltinTemplate ? (
          <div className="space-y-2">
            <div className="border-b border-slate-100 pb-2 text-sm leading-6 text-slate-600">
              {previewBuiltinTemplate.description || '暂无说明'}
            </div>
            <Tabs
              items={[
                {
                  key: 'build',
                  label: '构建',
                  children: (
                    <TemplatePreviewStage
                      stage="build"
                      variables={renderPreviewVariableCards(parseVariablesSchema(previewBuiltinTemplate.variablesSchema), 'build')}
                      scriptContent={previewBuiltinTemplate.buildScriptContent}
                      templateVariablePhases={buildTemplateVariablePhaseMap(parseVariablesSchema(previewBuiltinTemplate.variablesSchema))}
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
                      variables={renderPreviewVariableCards(parseVariablesSchema(previewBuiltinTemplate.variablesSchema), 'deploy')}
                      scriptContent={previewBuiltinTemplate.deployScriptContent}
                      templateVariablePhases={buildTemplateVariablePhaseMap(parseVariablesSchema(previewBuiltinTemplate.variablesSchema))}
                      shellVariables={shellVariables}
                    />
                  ),
                },
              ]}
            />
          </div>
        ) : null}
      </Modal>
    </>
  );
}
