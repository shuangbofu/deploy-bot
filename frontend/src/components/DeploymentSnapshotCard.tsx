import { useMemo, useState } from 'react';
import { Button, Card, Collapse, Descriptions, Modal } from 'antd';
import type { DeploymentPluginDefinitionSummary, PluginFormFieldSummary, RuntimeEnvironmentSummary } from '../types/domain';
import CodeEditor from './CodeEditor';

interface Props {
  loading?: boolean;
  executionSnapshot: Record<string, unknown> | null;
  pipelinePluginId?: string | null;
  plugins: DeploymentPluginDefinitionSummary[];
  embedded?: boolean;
}

const inferCodeLanguage = (field?: PluginFormFieldSummary) => {
  const text = [field?.key, field?.label, field?.helpText].filter(Boolean).join(' ').toLowerCase();
  if (text.includes('yaml') || text.includes('yml')) {
    return 'yaml';
  }
  if (text.includes('shell') || text.includes('script') || text.includes('command')) {
    return 'shell';
  }
  if (text.includes('json')) {
    return 'json';
  }
  if (text.includes('xml')) {
    return 'xml';
  }
  return 'text';
};

const readRuntimeEnvironments = (
  executionSnapshot: Record<string, unknown> | null,
  key: string,
  fallbackKeys: string[],
): RuntimeEnvironmentSummary[] => {
  const value = executionSnapshot?.[key];
  if (Array.isArray(value)) {
    return value.filter((item): item is RuntimeEnvironmentSummary => Boolean(item && typeof item === 'object'));
  }
  return fallbackKeys
    .map((fallbackKey) => executionSnapshot?.[fallbackKey])
    .filter((item): item is RuntimeEnvironmentSummary => Boolean(item && typeof item === 'object'));
};

const stringifySnapshotValue = (value: unknown) => String(value ?? '').trim();

export default function DeploymentSnapshotCard({ loading, executionSnapshot, pipelinePluginId, plugins, embedded = false }: Props) {
  const [previewField, setPreviewField] = useState<{ label: string; value: string; language: string } | null>(null);
  const pluginId = stringifySnapshotValue(executionSnapshot?.pluginId) || pipelinePluginId || undefined;
  const plugin = plugins.find((item) => item.descriptor.pluginId === pluginId) || null;
  const pluginFieldMap = useMemo(() => {
    const fields = (plugin?.pipelineFormSchema?.sections || []).flatMap((section) => section.fields || []);
    return new Map(fields.map((field) => [field.key, field]));
  }, [plugin]);
  const snapshotVariables = useMemo(() => {
    const raw = executionSnapshot?.variables;
    if (Array.isArray(raw)) {
      return raw.map((item) => {
        const entry = item as { name?: unknown; label?: unknown; value?: unknown };
        const name = String(entry.name ?? '-');
        return {
          name,
          label: String(entry.label ?? name),
          value: String(entry.value ?? '-'),
        };
      });
    }
    return Object.entries((raw as Record<string, unknown> | undefined) || {}).map(([name, value]) => ({
      name,
      label: name,
      value: String(value ?? '-'),
    }));
  }, [executionSnapshot]);
  const snapshotFields = useMemo(() => {
    const items: Array<{ label: string; value: string; field?: PluginFormFieldSummary }> = [];
    const pushField = (label: string, value?: unknown, field?: PluginFormFieldSummary) => {
      const text = stringifySnapshotValue(value);
      if (!text || text === '-') {
        return;
      }
      items.push({ label, value: text, field });
    };
    const envText = (env?: RuntimeEnvironmentSummary | null) => {
      const name = stringifySnapshotValue(env?.name);
      const version = stringifySnapshotValue(env?.version);
      return [name, version].filter(Boolean).join(' ');
    };
    const pushRuntimeField = (labelPrefix: string, env?: RuntimeEnvironmentSummary | null) => {
      const type = stringifySnapshotValue(env?.type);
      pushField(type ? `${labelPrefix} ${type}` : labelPrefix, envText(env));
    };

    pushField('模板', executionSnapshot?.templateName);
    pushField('模板类型', executionSnapshot?.templateType);
    pushField('目标主机', executionSnapshot?.targetHost);
    pushField('部署目录', executionSnapshot?.targetDir);
    pushField('服务名', executionSnapshot?.serviceName);
    const pluginConfig = executionSnapshot?.pluginConfig;
    if (pluginConfig && typeof pluginConfig === 'object' && !Array.isArray(pluginConfig)) {
      Object.entries(pluginConfig).forEach(([key, value]) => {
        const field = pluginFieldMap.get(key);
        pushField(field?.label || key, value, field);
      });
    }
    pushField('启动关键字', executionSnapshot?.startupKeyword);
    pushField('启动超时', executionSnapshot?.startupTimeoutSeconds);
    readRuntimeEnvironments(executionSnapshot, 'buildRuntimeEnvironments', ['javaEnvironment', 'nodeEnvironment', 'mavenEnvironment'])
      .forEach((item) => pushRuntimeField('构建组件', item));
    readRuntimeEnvironments(executionSnapshot, 'targetRuntimeEnvironments', ['runtimeJavaEnvironment'])
      .forEach((item) => pushRuntimeField('运行组件', item));

    return items;
  }, [executionSnapshot, pluginFieldMap]);

  const content = (
    <>
      <Descriptions column={1} size="small">
        {snapshotFields.length > 0 ? snapshotFields.map((item) => (
          <Descriptions.Item key={item.label} label={item.label}>
            {item.field?.type === 'CODE' ? (
              <Button
                size="small"
                onClick={() => setPreviewField({
                  label: item.label,
                  value: item.value,
                  language: inferCodeLanguage(item.field),
                })}
              >
                查看
              </Button>
            ) : item.value}
          </Descriptions.Item>
        )) : (
          <Descriptions.Item label="快照信息">-</Descriptions.Item>
        )}
      </Descriptions>
      <Collapse
        className="mt-4 deployment-detail-variables-collapse"
        ghost
        items={[
          {
            key: 'variables',
            label: '变量快照',
            children: (
              <>
                {snapshotVariables.length > 0 ? (
                  <div className="deployment-snapshot-variable-table">
                    {snapshotVariables.map((item) => (
                      <div key={item.name} className="deployment-snapshot-variable-row">
                        <div className="deployment-snapshot-variable-name">{item.label}</div>
                        <code className="deployment-snapshot-variable-value">{item.value}</code>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="rounded-lg border border-dashed border-slate-200 px-3 py-4 text-sm text-slate-400">
                    暂无变量快照
                  </div>
                )}
              </>
            ),
          },
        ]}
      />
      <Modal
        title={previewField?.label}
        open={Boolean(previewField)}
        footer={null}
        width={860}
        destroyOnHidden
        onCancel={() => setPreviewField(null)}
      >
        {previewField ? (
          <CodeEditor
            readOnly
            rows={18}
            value={previewField.value}
            language={previewField.language}
            onChange={() => undefined}
          />
        ) : null}
      </Modal>
    </>
  );

  if (embedded) {
    return content;
  }

  return (
    <Card className="app-card deployment-detail-snapshot-card" title="部署快照" loading={loading}>
      {content}
    </Card>
  );
}
