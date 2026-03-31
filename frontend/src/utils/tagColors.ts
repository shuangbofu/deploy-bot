const TAG_PALETTE = ['#0f766e', '#1d4ed8', '#b45309', '#be123c', '#6d28d9', '#0f172a', '#0369a1', '#166534', '#9a3412', '#4338ca'];

export function getStableTagColor(tag: string) {
  let hash = 0;
  for (let index = 0; index < tag.length; index += 1) {
    hash = (hash * 31 + tag.charCodeAt(index)) >>> 0;
  }
  return TAG_PALETTE[hash % TAG_PALETTE.length];
}
