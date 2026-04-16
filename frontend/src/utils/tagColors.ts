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

const TAG_COLOR_STOPS: Array<[number, number, number]> = [
  [239, 68, 68],
  [249, 115, 22],
  [250, 204, 21],
  [132, 204, 22],
  [45, 212, 191],
  [56, 189, 248],
  [96, 165, 250],
  [129, 140, 248],
  [168, 85, 247],
  [244, 114, 182],
  [239, 68, 68],
];

function interpolate(start: number, end: number, ratio: number) {
  return Math.round(start + (end - start) * ratio);
}

function rgb([red, green, blue]: [number, number, number]) {
  return `rgb(${red}, ${green}, ${blue})`;
}

function colorAtRatio(ratio: number) {
  const scaled = ratio * (TAG_COLOR_STOPS.length - 1);
  const leftIndex = Math.floor(scaled);
  const rightIndex = Math.min(leftIndex + 1, TAG_COLOR_STOPS.length - 1);
  const localRatio = scaled - leftIndex;
  const start = TAG_COLOR_STOPS[leftIndex];
  const end = TAG_COLOR_STOPS[rightIndex];
  return rgb([
    interpolate(start[0], end[0], localRatio),
    interpolate(start[1], end[1], localRatio),
    interpolate(start[2], end[2], localRatio),
  ]);
}

function hashTag(tag: string) {
  let hash = 2166136261;
  for (let index = 0; index < tag.length; index += 1) {
    hash ^= tag.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function mixHash(hash: number) {
  let mixed = hash >>> 0;
  mixed ^= mixed >>> 16;
  mixed = Math.imul(mixed, 0x7feb352d);
  mixed ^= mixed >>> 15;
  mixed = Math.imul(mixed, 0x846ca68b);
  mixed ^= mixed >>> 16;
  return mixed >>> 0;
}

export function getStableTagColor(tag: string) {
  const mixed = mixHash(hashTag(tag));
  const ratio = mixed / 0xffffffff;
  return colorAtRatio(ratio);
}

export function sortTagNames(tags: string[]) {
  return tags.slice().sort((left, right) => left.localeCompare(right, 'zh-CN'));
}

export function sortByPhase<T extends { phase?: 'build' | 'deploy' | 'shared' }>(items: T[]) {
  return items
    .slice()
    .sort((left, right) => (PHASE_SORT_ORDER[left.phase || 'shared'] ?? 99) - (PHASE_SORT_ORDER[right.phase || 'shared'] ?? 99));
}
