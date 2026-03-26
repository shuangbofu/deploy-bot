/**
 * 把两个时间点之间的差值格式化成适合列表展示的中文时长。
 */
export function formatDurationSince(startAt?: string | null, endAt?: string | null): string {
  if (!startAt) {
    return '-';
  }
  const start = new Date(startAt).getTime();
  const end = endAt ? new Date(endAt).getTime() : Date.now();
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) {
    return '-';
  }
  const totalSeconds = Math.floor((end - start) / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) {
    return `${hours}小时${minutes}分`;
  }
  if (minutes > 0) {
    return `${minutes}分${seconds}秒`;
  }
  return `${seconds}秒`;
}
