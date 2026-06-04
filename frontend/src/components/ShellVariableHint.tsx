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
  const stageHint = stage === 'build'
    ? '当前是构建脚本：源码目录用 $BUILD_SOURCE_DIR，构建产物写入 $ARTIFACT_DIR。'
    : '当前是发布脚本：部署目录用 $TARGET_DIR，发布产物从 $ARTIFACT_DIR 读取；不要在发布脚本里直接读本机构建源码目录。';
  const items = variables.filter((item) => {
    if (!['ALL', currentStage].includes(item.stage)) {
      return false;
    }
    return true;
  });
  return (
    <Collapse
      ghost
      className="shell-variable-hint mb-3"
      items={[{
        key: stage,
        label: <span className="shell-variable-hint__title">可直接使用的 Shell 变量</span>,
        children: (
          <div className="space-y-3 text-xs">
            <div className="shell-variable-hint__intro">
              <code>{'{{xxx}}'}</code> 只表示模板变量，会展示给流水线填写；下面这些 <code>$XXX</code> 由平台在脚本执行前注入，不需要在模板变量里定义。
            </div>
            <div className="shell-variable-hint__intro">
              {stageHint}
            </div>
            <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
              {items.map((item) => {
                const used = extractVariableNames(item.expression).some((variableName) => usedVariables.has(variableName));
                return (
                <div
                  key={`${stage}-${item.key}`}
                  className={[
                    'shell-variable-hint__item',
                    used ? 'shell-variable-hint__item--used' : '',
                  ].join(' ')}
                >
                  <div className="flex items-center gap-2">
                    <code className="shell-variable-hint__code">{item.expression}</code>
                    {used ? <span className="shell-variable-hint__used-badge">已使用</span> : null}
                  </div>
                  <div className="shell-variable-hint__description">{item.description}</div>
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
