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
              <DeploymentSnapshotCard
                embedded
                loading={false}
                executionSnapshot={executionSnapshot}
                pipelinePluginId={pipelinePluginId}
                plugins={plugins}
              />
            ),
          },
          {
            key: 'diff',
            label: '部署差异',
            children: (
              <DeploymentGitDiffCard
                embedded
                loading={false}
                commitSha={commitSha}
                gitDiffSnapshot={gitDiffSnapshot}
              />
            ),
          },
        ]}
      />
    </Card>
  );
}
