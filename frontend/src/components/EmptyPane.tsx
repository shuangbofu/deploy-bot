/**
 * 统一空状态容器，避免每个页面都重复包一层间距样式。
 */
export default function EmptyPane({ description }) {
  return (
    <div className="empty-pane">
      <div className="empty-pane__mark">
        <span />
        <span />
        <span />
      </div>
      <div className="empty-pane__text">{description}</div>
    </div>
  );
}
