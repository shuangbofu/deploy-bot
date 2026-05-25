import { useLayoutEffect, useRef, useState } from 'react';
import { Button } from 'antd';

type LogViewerProps = {
  /** 原始日志内容。 */
  content: string;
  /** 日志区域最大高度，超出后内部滚动。 */
  maxHeight?: number;
  /** 日志仍在刷新时才允许暂停自动跟随。 */
  autoScrollAvailable?: boolean;
  /** 运行中但暂时没有新日志时显示的临时提示，不写入日志正文。 */
  idleHint?: string;
};

const normalizeTerminalLogLines = (content: string) => {
  const text = content || '暂无日志输出。';
  const lines: string[] = [];
  let currentLine = '';
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (char === '\r') {
      currentLine = '';
      continue;
    }
    if (char === '\n') {
      lines.push(currentLine);
      currentLine = '';
      continue;
    }
    currentLine += char;
  }
  if (currentLine || lines.length === 0) {
    lines.push(currentLine);
  }
  return lines;
};

/**
 * 日志查看器。
 * 负责高亮命令追踪和错误行，并把页面滚动限制在日志容器内部。
 */
export default function LogViewer({ content, maxHeight, autoScrollAvailable = false, idleHint }: LogViewerProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [showBackToTop, setShowBackToTop] = useState(false);
  const [showBackToBottom, setShowBackToBottom] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const lines = normalizeTerminalLogLines(content);
  const updateScrollButtons = (element: HTMLDivElement) => {
    const bottomDistance = element.scrollHeight - element.scrollTop - element.clientHeight;
    setShowBackToTop(element.scrollTop > 120);
    setShowBackToBottom(bottomDistance > 80);
  };

  useLayoutEffect(() => {
    if (!containerRef.current) {
      return;
    }
    if (autoScroll) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
    updateScrollButtons(containerRef.current);
  }, [autoScroll, content]);

  useLayoutEffect(() => {
    if (!autoScrollAvailable) {
      setAutoScroll(true);
    }
  }, [autoScrollAvailable]);

  const scrollToTop = () => {
    if (!containerRef.current) {
      return;
    }
    containerRef.current.scrollTo({ top: 0, behavior: 'smooth' });
  };
  const scrollToBottom = () => {
    if (!containerRef.current) {
      return;
    }
    containerRef.current.scrollTo({ top: containerRef.current.scrollHeight, behavior: 'smooth' });
  };
  const toggleAutoScroll = () => {
    const nextAutoScroll = !autoScroll;
    setAutoScroll(nextAutoScroll);
    if (nextAutoScroll) {
      window.requestAnimationFrame(() => scrollToBottom());
    }
  };

  return (
    <div className="log-viewer-shell">
      <div
        ref={containerRef}
        className="log-viewer"
        style={maxHeight ? { maxHeight } : undefined}
        onScroll={(event) => updateScrollButtons(event.currentTarget)}
      >
        {lines.map((line, index) => {
          const lowerLine = line.toLowerCase();
          const isSystemLine = line.startsWith('[系统]');
          const isCommandLine = /^\+{1,3}\s/.test(line);
          // 这里优先照顾运维排查体验，对常见错误关键词做红色高亮。
          const isErrorLine = /(error|failed|fatal|exception|denied|refused|timed out|认证失败|失败|报错|错误)/.test(lowerLine);
          const lineClassName = [
            'log-line',
            isSystemLine ? 'log-line-system' : '',
            isCommandLine ? 'log-line-command' : '',
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
        {idleHint ? (
          <div className="log-line log-line-idle-hint">
            {idleHint}
          </div>
        ) : null}
      </div>
      <div className="log-viewer-actions">
        {showBackToTop ? (
          <Button size="small" onClick={scrollToTop}>回到顶部</Button>
        ) : null}
        {showBackToBottom ? (
          <Button size="small" onClick={scrollToBottom}>回到底部</Button>
        ) : null}
        {autoScrollAvailable ? (
          <Button size="small" onClick={toggleAutoScroll}>
            {autoScroll ? '暂停滚动' : '恢复滚动'}
          </Button>
        ) : null}
      </div>
    </div>
  );
}
