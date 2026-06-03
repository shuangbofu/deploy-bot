import { forwardRef, useEffect, useImperativeHandle, useLayoutEffect, useMemo, useRef, useState } from 'react';

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
  const text = content || '';
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
  if (currentLine || lines.length > 0) {
    lines.push(currentLine);
  }
  return lines;
};

const ERROR_LINE_PATTERN = /(error|failed|fatal|exception|denied|refused|timed out|BUILD FAILURE|npm ERR|认证失败|失败|报错|错误)/i;

const STACK_TRACE_LINE_PATTERN = /^\s*(at\s|Caused by:|Suppressed:|\.\.\. \d+ more)/;
const LOG_LINE_HEIGHT = 22;
const LOG_RENDER_OVERSCAN = 24;
const LOG_ANCHOR_SCAN_LIMIT = 5000;

const isFlowAnchorLine = (line: string) => /^\[步骤\s*\d+\/\d+]/.test(line)
  || /^\[完成]/.test(line)
  || /^\[系统].*(构建完成|发布阶段|部署失败|启动观察未通过|服务检测超时|部署执行异常)/.test(line);

const isErrorAnchorLine = (line: string) => {
  if (STACK_TRACE_LINE_PATTERN.test(line)) {
    return false;
  }
  return /^\[系统].*(失败|异常|错误|超时)/.test(line)
    || /^npm ERR/i.test(line)
    || /\b(BUILD FAILURE|fatal:|Error:|Exception:)\b/i.test(line);
};

const buildLogAnchors = (lines: string[]) => {
  const flowAnchors = lines
    .map((line, index) => ({ line, index, isError: false }))
    .filter(({ line }) => isFlowAnchorLine(line));
  const errorAnchors = lines
    .map((line, index) => ({ line, index, isError: true }))
    .filter(({ line }) => isErrorAnchorLine(line))
    .slice(-8);
  return [...flowAnchors, ...errorAnchors]
    .sort((left, right) => left.index - right.index)
    .filter((item, index, source) => index === 0 || item.index !== source[index - 1].index)
    .slice(-24)
    .map(({ line, index, isError }) => ({
    index,
    level: isError ? 'error' as const : 'normal' as const,
    label: line.replace(/^\[系统]\s*\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\s*/, '[系统] ').slice(0, 34),
  }));
};

const appendLogContentToLines = (currentLines: string[], delta: string, reset: boolean) => {
  const nextLines = reset ? [] : currentLines.slice(0, Math.max(0, currentLines.length - 1));
  let currentLine = reset ? '' : (currentLines[currentLines.length - 1] || '');
  for (let index = 0; index < delta.length; index += 1) {
    const char = delta[index];
    if (char === '\r') {
      currentLine = '';
      continue;
    }
    if (char === '\n') {
      nextLines.push(currentLine);
      currentLine = '';
      continue;
    }
    currentLine += char;
  }
  nextLines.push(currentLine);
  return nextLines;
};

/**
 * 日志查看器。
 * 负责高亮命令追踪和错误行，并把页面滚动限制在日志容器内部。
 */
const LogViewer = forwardRef<LogViewerHandle, LogViewerProps>(function LogViewer(
  { content, maxHeight, autoScrollAvailable = false, idleHint, onControlStateChange, onAnchorsChange },
  ref,
) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const previousContentRef = useRef('');
  const [showBackToTop, setShowBackToTop] = useState(false);
  const [showBackToBottom, setShowBackToBottom] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const [lines, setLines] = useState<string[]>(() => normalizeTerminalLogLines(content));
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(0);
  const onControlStateChangeRef = useRef(onControlStateChange);
  const onAnchorsChangeRef = useRef(onAnchorsChange);
  const anchorStartIndex = Math.max(0, lines.length - LOG_ANCHOR_SCAN_LIMIT);
  const anchors = useMemo(() => (
    buildLogAnchors(lines.slice(anchorStartIndex))
      .map((anchor) => ({ ...anchor, index: anchor.index + anchorStartIndex }))
  ), [anchorStartIndex, lines]);
  const anchorSignature = useMemo(() => anchors.map((anchor) => `${anchor.index}:${anchor.label}`).join('|'), [anchors]);
  const updateScrollButtons = (element: HTMLDivElement) => {
    const bottomDistance = element.scrollHeight - element.scrollTop - element.clientHeight;
    setScrollTop(element.scrollTop);
    setViewportHeight(element.clientHeight);
    setShowBackToTop(element.scrollTop > 120);
    setShowBackToBottom(bottomDistance > 80);
  };
  const visibleRange = useMemo(() => {
    const start = Math.max(0, Math.floor(scrollTop / LOG_LINE_HEIGHT) - LOG_RENDER_OVERSCAN);
    const end = Math.min(
      lines.length,
      Math.ceil((scrollTop + viewportHeight) / LOG_LINE_HEIGHT) + LOG_RENDER_OVERSCAN,
    );
    return { start, end: Math.max(start + 1, end) };
  }, [lines.length, scrollTop, viewportHeight]);
  const visibleLines = useMemo(
    () => lines.slice(visibleRange.start, visibleRange.end),
    [lines, visibleRange.end, visibleRange.start],
  );
  const totalHeight = lines.length * LOG_LINE_HEIGHT + 24;
  const contentTop = 12;

  useEffect(() => {
    const previousContent = previousContentRef.current;
    if (!content) {
      previousContentRef.current = '';
      setLines([]);
      return;
    }
    if (previousContent && content.startsWith(previousContent)) {
      const delta = content.slice(previousContent.length);
      previousContentRef.current = content;
      if (delta) {
        setLines((current) => appendLogContentToLines(current, delta, false));
      }
      return;
    }
    previousContentRef.current = content;
    setLines(appendLogContentToLines([], content, true));
  }, [content]);

  useLayoutEffect(() => {
    if (!containerRef.current) {
      return;
    }
    if (autoScroll) {
      const bottomTop = Math.max(0, totalHeight - containerRef.current.clientHeight);
      setScrollTop(bottomTop);
      containerRef.current.scrollTop = bottomTop;
    }
    updateScrollButtons(containerRef.current);
  }, [autoScroll, idleHint, lines.length, totalHeight]);

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
    const bottomTop = Math.max(0, totalHeight - containerRef.current.clientHeight);
    setScrollTop(bottomTop);
    window.requestAnimationFrame(() => {
      if (containerRef.current) {
        containerRef.current.scrollTop = bottomTop;
        updateScrollButtons(containerRef.current);
      }
    });
  };
  const toggleAutoScroll = () => {
    const nextAutoScroll = !autoScroll;
    setAutoScroll(nextAutoScroll);
    if (nextAutoScroll) {
      window.requestAnimationFrame(() => scrollToBottom());
    }
  };
  const scrollToLine = (lineIndex: number) => {
    containerRef.current?.scrollTo({ top: Math.max(0, contentTop + lineIndex * LOG_LINE_HEIGHT - LOG_LINE_HEIGHT * 6), behavior: 'smooth' });
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
        <div className="log-viewer-virtual-space" style={{ height: totalHeight }}>
          <div
            className="log-viewer-visible-lines"
            style={{ transform: `translateY(${contentTop + visibleRange.start * LOG_LINE_HEIGHT}px)` }}
          >
            {visibleLines.map((line, index) => {
              const lineIndex = visibleRange.start + index;
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
                  key={lineIndex}
                  className={lineClassName}
                  data-log-line={lineIndex}
                >
                  {line || ' '}
                </div>
              );
            })}
          </div>
          {idleHint ? (
            <div className="log-line log-line-idle-hint log-viewer-idle-hint">
              {idleHint}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
});

export default LogViewer;
