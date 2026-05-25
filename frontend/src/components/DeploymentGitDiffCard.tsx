import { Card, Tag, Timeline, Typography } from 'antd';
import EmptyPane from './EmptyPane';
import { formatDateTime } from '../utils/datetime';

type Props = {
  loading?: boolean;
  commitSha?: string | null;
  gitDiffSnapshot?: Record<string, unknown> | null;
  embedded?: boolean;
};

const stringValue = (value: unknown) => String(value ?? '').trim();

const arrayValue = (value: unknown) => (Array.isArray(value) ? value : []);

const gitFileStatusMeta = {
  A: { label: '新增', className: 'git-diff-file-tag--added' },
  M: { label: '修改', className: 'git-diff-file-tag--modified' },
  D: { label: '删除', className: 'git-diff-file-tag--deleted' },
  R: { label: '重命名', className: 'git-diff-file-tag--renamed' },
} as const;

const resolveGitFileStatusMeta = (status: string) => {
  const key = status.trim().charAt(0) as keyof typeof gitFileStatusMeta;
  return gitFileStatusMeta[key] || { label: status || '变更', className: 'git-diff-file-tag--modified' };
};

export default function DeploymentGitDiffCard({ loading, commitSha, gitDiffSnapshot, embedded = false }: Props) {
  const snapshot = gitDiffSnapshot || {};
  const commits = arrayValue(snapshot.commits);
  const files = arrayValue(snapshot.files);
  const message = stringValue(snapshot.message);
  const currentShortCommit = stringValue(snapshot.currentShortCommit) || (commitSha ? commitSha.slice(0, 8) : '');
  const previousShortCommit = stringValue(snapshot.previousShortCommit);
  const commitCount = Number(snapshot.commitCount ?? commits.length);
  const stat = stringValue(snapshot.stat);

  const content = !commitSha && !currentShortCommit ? (
    <EmptyPane description="这次部署还没有记录 Git 提交。" />
  ) : (
    <div className="deployment-git-diff">
          <div className="deployment-git-diff__summary">
            <div>
              <span>本次</span>
              <strong>{currentShortCommit || '-'}</strong>
            </div>
            <div>
              <span>上次成功</span>
              <strong>{previousShortCommit || '-'}</strong>
            </div>
            <div>
              <span>提交数</span>
              <strong>{Number.isFinite(commitCount) ? commitCount : 0}</strong>
            </div>
          </div>
          {message ? <div className="deployment-git-diff__message">{message}</div> : null}
          {commits.length > 0 ? (
            <Timeline
              className="deployment-git-diff__timeline"
              items={commits.map((raw) => {
                const item = raw as Record<string, unknown>;
                const authorName = stringValue(item.authorName);
                const authorEmail = stringValue(item.authorEmail);
                const date = stringValue(item.date);
                return {
                  children: (
                    <div className="deployment-git-diff__commit">
                      <div className="deployment-git-diff__commit-title">
                        <Tag className="app-muted-tag !border-0">{stringValue(item.shortSha)}</Tag>
                        <Typography.Text strong>{stringValue(item.message) || '无提交说明'}</Typography.Text>
                      </div>
                      <div className="deployment-git-diff__commit-meta">
                        {authorName || '-'}{authorEmail ? ` <${authorEmail}>` : ''} · {formatDateTime(date)}
                      </div>
                    </div>
                  ),
                };
              })}
            />
          ) : null}
          {files.length > 0 ? (
            <div className="deployment-git-diff__files">
              {files.slice(0, 30).map((raw, index) => {
                const item = raw as Record<string, unknown>;
                const status = stringValue(item.status);
                const statusMeta = resolveGitFileStatusMeta(status);
                const path = stringValue(item.path);
                const newPath = stringValue(item.newPath);
                return (
                  <div className="deployment-git-diff__file" key={`${status}-${path}-${index}`}>
                    <span className={`git-diff-file-tag ${statusMeta.className}`}>{statusMeta.label}</span>
                    <span className="deployment-git-diff__file-path">{newPath ? `${path} -> ${newPath}` : path}</span>
                  </div>
                );
              })}
              {files.length > 30 ? <div className="deployment-git-diff__more">还有 {files.length - 30} 个文件未展开</div> : null}
            </div>
          ) : null}
          {stat ? <pre className="deployment-git-diff__stat">{stat}</pre> : null}
    </div>
  );

  if (embedded) {
    return content;
  }

  return (
    <Card className="app-card deployment-git-diff-card" title="部署差异" loading={loading}>
      {content}
    </Card>
  );
}
