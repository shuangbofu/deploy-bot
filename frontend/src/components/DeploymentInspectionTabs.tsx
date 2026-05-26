import { Card, Tabs } from 'antd';
import type { DeploymentPluginDefinitionSummary } from '../types/domain';
import DeploymentGitDiffCard from './DeploymentGitDiffCard';
import DeploymentSnapshotCard from './DeploymentSnapshotCard';

type Props = {
  loading?: boolean;
  executionSnapshot: Record<string, unknown> | null;
  pipelinePluginId?: string | null;
  plugins: DeploymentPluginDefinitionSummary[];
  commitSha?: string | null;
  gitDiffSnapshot?: Record<string, unknown> | null;
};

export default function DeploymentInspectionTabs({
  loading,
  executionSnapshot,
  pipelinePluginId,
  plugins,
  commitSha,
  gitDiffSnapshot,
}: Props) {
  return (
    <Card className="app-card deployment-inspection-card" loading={loading}>
      <Tabs
        className="deployment-inspection-tabs"
        defaultActiveKey="snapshot"
        items={[
          {
            key: 'snapshot',
            label: '部署快照',
            children: (
              <div className="deployment-inspection-pane">
                <DeploymentSnapshotCard
                  embedded
                  loading={false}
                  executionSnapshot={executionSnapshot}
                  pipelinePluginId={pipelinePluginId}
                  plugins={plugins}
                />
              </div>
            ),
          },
          {
            key: 'diff',
            label: '部署差异',
            children: (
              <div className="deployment-inspection-pane">
                <DeploymentGitDiffCard
                  embedded
                  loading={false}
                  commitSha={commitSha}
                  gitDiffSnapshot={gitDiffSnapshot}
                />
              </div>
            ),
          },
        ]}
      />
    </Card>
  );
}
