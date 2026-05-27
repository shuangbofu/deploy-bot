import { forwardRef, useImperativeHandle, useLayoutEffect, useMemo, useRef, useState } from 'react';

export type LogAnchor = {
  index: number;
  label: string;
  level: 'normal' | 'error';
};

type LogViewerProps = {
  /** 原始日志内容。 */
  content: string;
  /** 日志区域最大高度，超出后内部滚动。 */
  maxHeight?: number;
  /** 日志仍在刷新时才允许暂停自动跟随。 */
  autoScrollAvailable?: boolean;
  /** 运行中但暂时没有新日志时显示的临时提示，不写入日志正文。 */
  idleHint?: string;
  /** 滚动控制状态变化时通知父组件刷新按钮。 */
  onControlStateChange?: () => void;
  /** 日志目录变化时通知父组件渲染顶部目录按钮。 */
  onAnchorsChange?: (anchors: LogAnchor[]) => void;
};

export type LogViewerHandle = {
  canBackToTop: boolean;
  canBackToBottom: boolean;
  autoScroll: boolean;
  scrollToTop: () => void;
  scrollToBottom: () => void;
  toggleAutoScroll: () => void;
  scrollToLine: (lineIndex: number) => void;
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

const ERROR_LINE_PATTERN = /(error|failed|fatal|exception|denied|refused|timed out|BUILD FAILURE|npm ERR|认证失败|失败|报错|错误)/i;

const buildLogAnchors = (lines: string[]) => lines
  .map((line, index) => ({ line, index, isError: ERROR_LINE_PATTERN.test(line) }))
  .filter(({ line, isError }) => /^\[步骤\s*\d+\/\d+]/.test(line)
    || /^\[完成]/.test(line)
    || /^\[系统].*(构建完成|发布阶段|部署失败|启动观察未通过|服务检测超时)/.test(line)
    || isError)
  .slice(-18)
  .map(({ line, index, isError }) => ({
    index,
    level: isError ? 'error' as const : 'normal' as const,
    label: line.replace(/^\[系统]\s*\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\s*/, '[系统] ').slice(0, 34),
  }));

/**
 * 日志查看器。
 * 负责高亮命令追踪和错误行，并把页面滚动限制在日志容器内部。
 */
const LogViewer = forwardRef<LogViewerHandle, LogViewerProps>(function LogViewer(
  { content, maxHeight, autoScrollAvailable = false, idleHint, onControlStateChange, onAnchorsChange },
  ref,
) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [showBackToTop, setShowBackToTop] = useState(false);
  const [showBackToBottom, setShowBackToBottom] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const onControlStateChangeRef = useRef(onControlStateChange);
  const onAnchorsChangeRef = useRef(onAnchorsChange);
  const lines = useMemo(() => normalizeTerminalLogLines(content), [content]);
  const anchors = useMemo(() => buildLogAnchors(lines), [lines]);
  const anchorSignature = useMemo(() => anchors.map((anchor) => `${anchor.index}:${anchor.label}`).join('|'), [anchors]);
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
  }, [autoScroll, content, idleHint]);

  useLayoutEffect(() => {
    if (!autoScrollAvailable) {
      setAutoScroll(true);
    }
  }, [autoScrollAvailable]);

  useLayoutEffect(() => {
    onControlStateChangeRef.current = onControlStateChange;
  }, [onControlStateChange]);

  useLayoutEffect(() => {
    onAnchorsChangeRef.current = onAnchorsChange;
  }, [onAnchorsChange]);

  useLayoutEffect(() => {
    onControlStateChangeRef.current?.();
  }, [autoScroll, showBackToBottom, showBackToTop]);

  useLayoutEffect(() => {
    onAnchorsChangeRef.current?.(anchors);
  }, [anchorSignature, anchors]);

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
  const scrollToLine = (lineIndex: number) => {
    const element = containerRef.current?.querySelector<HTMLElement>(`[data-log-line="${lineIndex}"]`);
    element?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  useImperativeHandle(ref, () => ({
    canBackToTop: showBackToTop,
    canBackToBottom: showBackToBottom,
    autoScroll,
    scrollToTop,
    scrollToBottom,
    toggleAutoScroll,
    scrollToLine,
  }), [autoScroll, showBackToBottom, showBackToTop]);

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
          const isErrorLine = ERROR_LINE_PATTERN.test(lowerLine);
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
              data-log-line={index}
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
    </div>
  );
});

export default LogViewer;
