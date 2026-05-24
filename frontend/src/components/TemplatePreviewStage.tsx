import type { ReactNode } from 'react';
import CodeEditor from './CodeEditor';
import EmptyPane from './EmptyPane';
import ShellVariableHint from './ShellVariableHint';
import type { ShellVariableSummary } from '../types/domain';

type TemplatePreviewStageProps = {
  stage: 'build' | 'deploy';
  variables: ReactNode;
  scriptContent?: string | null;
  templateVariablePhases: Record<string, 'build' | 'deploy' | 'shared'>;
  shellVariables: ShellVariableSummary[];
};

/**
 * 默认模板详情中单个阶段的预览内容。
 */
export default function TemplatePreviewStage({
  stage,
  variables,
  scriptContent,
  templateVariablePhases,
  shellVariables,
}: TemplatePreviewStageProps) {
  const script = scriptContent || '';
  return (
    <div className="space-y-3 ">
      {variables ? (
        <div>
          <div className="mb-2 text-sm font-semibold text-slate-800">变量定义</div>
          {variables}
        </div>
      ) : null}
      <div>
        <div className="mb-3 text-sm font-semibold text-slate-800">脚本内容</div>
        <ShellVariableHint stage={stage} scriptContent={script} variables={shellVariables} />
        {script ? (
          <CodeEditor
            value={script}
            onChange={() => {}}
            language="shell"
            rows={Math.max(10, script.split('\n').length)}
            readOnly
            templateVariablePhases={templateVariablePhases}
          />
        ) : (
          <EmptyPane description={`这个默认模板没有单独的${stage === 'build' ? '构建' : '发布'}脚本。`} />
        )}
      </div>
    </div>
  );
}
