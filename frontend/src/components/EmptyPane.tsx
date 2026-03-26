import { Empty } from 'antd';

/**
 * 统一空状态容器，避免每个页面都重复包一层间距样式。
 */
export default function EmptyPane({ description }) {
  return (
    <div className="empty-pane">
      <Empty description={description} />
    </div>
  );
}
