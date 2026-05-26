import type { CSSProperties } from 'react';
import { Tag } from 'antd';
import { getStableTagColor, getStableTagDarkColor } from '../utils/tagColors';
import { normalizeTagList } from '../utils/pipelineDisplay';

const stableTagStyle = (tag: string): CSSProperties => ({
  '--app-tag-bg': getStableTagColor(tag),
  '--app-tag-bg-dark': getStableTagDarkColor(tag),
  '--app-tag-fg': '#ffffff',
} as CSSProperties);

type Props = {
  name?: string | null;
  importantTags?: unknown;
  fallback?: string;
  className?: string;
};

export default function PipelineNameWithTags({ name, importantTags, fallback = '-', className }: Props) {
  const displayName = name || fallback;
  const tags = normalizeTagList(importantTags).slice(0, 2);
  return (
    <span className={`pipeline-name-with-tags ${className || ''}`.trim()} title={displayName}>
      {tags.map((tag) => (
        <Tag
          key={tag}
          style={stableTagStyle(tag)}
          className="pipeline-important-tag app-color-tag !border-0"
        >
          {tag}
        </Tag>
      ))}
      <span className="pipeline-name-with-tags__text">{displayName}</span>
    </span>
  );
}
