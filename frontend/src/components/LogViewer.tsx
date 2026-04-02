import { useLayoutEffect, useRef } from 'react';

type LogViewerProps = {
  /** 原始日志内容。 */
  content: string;
  /** 日志区域最大高度，超出后内部滚动。 */
  maxHeight?: number;
};

/**
 * 日志查看器。
 * 负责高亮错误行，并把页面滚动限制在日志容器内部。
 */
export default function LogViewer({ content, maxHeight }: LogViewerProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const lines = (content || '暂无日志输出。').split('\n');

  // 日志页默认服务于排查场景，因此首次进入和自动刷新后都直接跟到最底部。
  useLayoutEffect(() => {
    if (!containerRef.current) {
      return;
    }
    containerRef.current.scrollTop = containerRef.current.scrollHeight;
  }, [content]);

  return (
    <div
      ref={containerRef}
      className="log-viewer"
      style={maxHeight ? { maxHeight } : undefined}
    >
      {lines.map((line, index) => {
        const lowerLine = line.toLowerCase();
        const isSystemLine = line.startsWith('[系统]');
        // 这里优先照顾运维排查体验，对常见错误关键词做红色高亮。
        const isErrorLine = /(error|failed|fatal|exception|denied|refused|timed out|认证失败|失败|报错|错误)/.test(lowerLine);
        const lineClassName = [
          'log-line',
          isSystemLine ? 'log-line-system' : '',
          isErrorLine ? 'log-line-error' : '',
        ].filter(Boolean).join(' ');

        return (
          <div
            key={`${index}-${line}`}
            className={lineClassName}
          >
            {line || ' '}
          </div>
        );
      })}
    </div>
  );
}
