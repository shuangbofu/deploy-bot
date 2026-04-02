const TAG_PALETTE = ['#0f766e', '#1d4ed8', '#b45309', '#be123c', '#6d28d9', '#0f172a', '#0369a1', '#166534', '#9a3412', '#4338ca'];

export const PHASE_LABEL_MAP = {
  build: '构建',
  deploy: '发布',
  shared: '共用',
} as const;

export const PHASE_TAG_COLOR_MAP = {
  build: '#2563eb',
  deploy: '#e11d48',
  shared: '#7c3aed',
} as const;

export const PHASE_SORT_ORDER = {
  build: 0,
  deploy: 1,
  shared: 2,
} as const;

export function getStableTagColor(tag: string) {
  let hash = 0;
  for (let index = 0; index < tag.length; index += 1) {
    hash = (hash * 31 + tag.charCodeAt(index)) >>> 0;
  }
  return TAG_PALETTE[hash % TAG_PALETTE.length];
}

export function sortByPhase<T extends { phase?: 'build' | 'deploy' | 'shared' }>(items: T[]) {
  return items
    .slice()
    .sort((left, right) => (PHASE_SORT_ORDER[left.phase || 'shared'] ?? 99) - (PHASE_SORT_ORDER[right.phase || 'shared'] ?? 99));
}
