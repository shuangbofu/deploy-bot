import type { ReactNode } from 'react';
import { Space, Typography } from 'antd';

type PageHeaderBarProps = {
  /** 页头主标题。 */
  title: ReactNode;
  /** 页头说明文案。 */
  description: ReactNode;
  /** 右侧操作区。 */
  extra?: ReactNode;
};

/**
 * 统一页面头部组件。
 * 负责标题、说明和操作按钮的标准化排版。
 */
export default function PageHeaderBar({ title, description, extra }: PageHeaderBarProps) {
  return (
    <div className="page-header">
      <div>
        <Typography.Title level={2} className="!mb-2 !mt-0">
          {title}
        </Typography.Title>
        <Typography.Paragraph className="!mb-0 page-header-text">
          {description}
        </Typography.Paragraph>
      </div>
      {extra ? <Space>{extra}</Space> : null}
    </div>
  );
}
