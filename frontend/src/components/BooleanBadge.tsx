type BooleanBadgeProps = {
  /** 当前布尔值。 */
  value: boolean;
  /** 为 true 时展示的文案。 */
  trueLabel?: string;
  /** 为 false 时展示的文案。 */
  falseLabel?: string;
};

/**
 * 轻量布尔值展示组件。
 * 与状态组件不同，这里只表达“是/否”而不是流程状态。
 */
export default function BooleanBadge({ value, trueLabel = '是', falseLabel = '否' }: BooleanBadgeProps) {
  return (
    <span className="status-chip">
      <span className={`status-dot ${value ? 'status-dot--success' : 'status-dot--pending'}`} />
      <span>{value ? trueLabel : falseLabel}</span>
    </span>
  );
}
