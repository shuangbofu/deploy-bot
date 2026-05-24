import { Collapse } from 'antd';
import type { ShellVariableSummary } from '../types/domain';

const extractVariableNames = (name: string) => Array.from(name.matchAll(/\$([A-Z0-9_]+)/g)).map((match) => match[1]);

const extractUsedShellVariables = (scriptContent?: string | null) => {
  const script = scriptContent || '';
  const used = new Set<string>();
  Array.from(script.matchAll(/\$\{?([A-Z][A-Z0-9_]*)}?/g)).forEach((match) => used.add(match[1]));
  return used;
};

export default function ShellVariableHint({
  stage,
  scriptContent,
  variables,
}: {
  stage: 'build' | 'deploy';
  scriptContent?: string | null;
  variables: ShellVariableSummary[];
}) {
  const usedVariables = extractUsedShellVariables(scriptContent);
  const currentStage = stage.toUpperCase();
  const items = variables.filter((item) => {
    if (!['ALL', currentStage].includes(item.stage)) {
      return false;
    }
    if (item.source !== 'PLUGIN') {
      return true;
    }
    return extractVariableNames(item.expression).some((variableName) => usedVariables.has(variableName));
  });
  return (
    <Collapse
      ghost
      className="mb-3 rounded-xl border border-slate-200 bg-slate-50"
      items={[{
        key: stage,
        label: <span className="text-sm font-medium text-slate-700">可直接使用的 Shell 变量</span>,
        children: (
          <div className="space-y-3 text-xs text-slate-600">
            <div>
              <code>{'{{xxx}}'}</code> 只表示模板变量，会展示给流水线填写；下面这些 <code>$XXX</code> 由平台在脚本执行前注入，不需要在模板变量里定义。
            </div>
            <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
              {items.map((item) => {
                const used = extractVariableNames(item.expression).some((variableName) => usedVariables.has(variableName));
                return (
                <div
                  key={`${stage}-${item.key}`}
                  className={[
                    'rounded-lg px-3 py-2',
                    used ? 'border border-emerald-300 bg-emerald-50 shadow-sm shadow-emerald-100' : 'bg-white',
                  ].join(' ')}
                >
                  <code className={used ? 'font-semibold text-emerald-800' : 'text-slate-900'}>{item.expression}</code>
                  {used ? <span className="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-medium text-emerald-700">已使用</span> : null}
                  <div className="mt-1 leading-5">{item.description}</div>
                </div>
                );
              })}
            </div>
          </div>
        ),
      }]}
    />
  );
}
