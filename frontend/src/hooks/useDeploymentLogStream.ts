import { useEffect, useRef } from 'react';
import { API_BASE_URL } from '../api/client';
import { authStorage } from '../auth/authStorage';

type LogStreamPayload = {
  content?: string;
  contentBase64?: string;
  offset?: number;
  finished?: boolean;
};

type Options = {
  deploymentId?: number | string;
  enabled: boolean;
  offset: number;
  onLog: (payload: LogStreamPayload) => void;
  onDone?: () => void;
  onError?: () => void;
};

const parseSseEvents = (buffer: string) => {
  const events: Array<{ event: string; data: string }> = [];
  let nextBuffer = buffer;
  let splitIndex = nextBuffer.indexOf('\n\n');
  while (splitIndex >= 0) {
    const rawEvent = nextBuffer.slice(0, splitIndex);
    nextBuffer = nextBuffer.slice(splitIndex + 2);
    const lines = rawEvent.split('\n');
    let event = 'message';
    const dataLines: string[] = [];
    lines.forEach((line) => {
      if (line.startsWith('event:')) {
        event = line.slice('event:'.length).trim();
      }
      if (line.startsWith('data:')) {
        dataLines.push(line.slice('data:'.length).trimStart());
      }
    });
    events.push({ event, data: dataLines.join('\n') });
    splitIndex = nextBuffer.indexOf('\n\n');
  }
  return { events, buffer: nextBuffer };
};

const resolveStreamUrl = (deploymentId: number | string, offset: number) => {
  const path = `${API_BASE_URL}/deployments/${deploymentId}/log/stream?offset=${Math.max(0, offset)}`;
  if (/^https?:\/\//i.test(path)) {
    return path;
  }
  return new URL(path, window.location.origin).toString();
};

const decodeBase64Utf8 = (value: string) => {
  const binary = window.atob(value);
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
};

export function useDeploymentLogStream({ deploymentId, enabled, offset, onLog, onDone, onError }: Options) {
  const offsetRef = useRef(offset);
  const onLogRef = useRef(onLog);
  const onDoneRef = useRef(onDone);
  const onErrorRef = useRef(onError);

  useEffect(() => {
    offsetRef.current = offset;
    onLogRef.current = onLog;
    onDoneRef.current = onDone;
    onErrorRef.current = onError;
  }, [offset, onDone, onError, onLog]);

  useEffect(() => {
    if (!enabled || !deploymentId) {
      return undefined;
    }
    const controller = new AbortController();
    const connect = async () => {
      try {
        const response = await fetch(resolveStreamUrl(deploymentId, offsetRef.current), {
          headers: {
            Authorization: `Bearer ${authStorage.getToken() || ''}`,
          },
          signal: controller.signal,
        });
        if (!response.ok || !response.body) {
          throw new Error('日志流连接失败');
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let done = false;
        while (!done) {
          const result = await reader.read();
          done = result.done;
          buffer += decoder.decode(result.value || new Uint8Array(), { stream: !done });
          const parsed = parseSseEvents(buffer);
          buffer = parsed.buffer;
          parsed.events.forEach((item) => {
            if (item.event === 'done') {
              onDoneRef.current?.();
              return;
            }
            if (item.event !== 'log' || !item.data) {
              return;
            }
            try {
              const payload = JSON.parse(item.data) as LogStreamPayload;
              onLogRef.current({
                ...payload,
                content: payload.contentBase64 ? decodeBase64Utf8(payload.contentBase64) : payload.content,
              });
            } catch {
              onLogRef.current({ content: item.data });
            }
          });
        }
      } catch {
        if (!controller.signal.aborted) {
          onErrorRef.current?.();
        }
      }
    };
    connect();
    return () => controller.abort();
  }, [deploymentId, enabled]);
}
