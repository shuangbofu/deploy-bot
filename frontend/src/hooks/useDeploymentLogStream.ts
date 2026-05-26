import { useEffect, useRef } from 'react';
import { API_BASE_URL, handleAuthExpired } from '../api/client';
import { authStorage } from '../auth/authStorage';
import type { DeploymentSummary } from '../types/domain';

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
  onDeployment?: (deployment: DeploymentSummary) => void;
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

export function useDeploymentLogStream({ deploymentId, enabled, offset, onLog, onDeployment, onDone, onError }: Options) {
  const offsetRef = useRef(offset);
  const onLogRef = useRef(onLog);
  const onDeploymentRef = useRef(onDeployment);
  const onDoneRef = useRef(onDone);
  const onErrorRef = useRef(onError);

  useEffect(() => {
    offsetRef.current = offset;
    onLogRef.current = onLog;
    onDeploymentRef.current = onDeployment;
    onDoneRef.current = onDone;
    onErrorRef.current = onError;
  }, [offset, onDeployment, onDone, onError, onLog]);

  useEffect(() => {
    if (!enabled || !deploymentId) {
      return undefined;
    }
    const controller = new AbortController();
    const connect = async () => {
      try {
        const token = authStorage.getToken();
        if (!token) {
          handleAuthExpired('AUTH-001');
          return;
        }
        const response = await fetch(resolveStreamUrl(deploymentId, offsetRef.current), {
          headers: {
            Authorization: `Bearer ${token}`,
          },
          signal: controller.signal,
        });
        if (!response.ok || !response.body) {
          if (response.status === 401 || response.status === 403) {
            try {
              const payload = await response.clone().json();
              handleAuthExpired(payload?.subCode);
            } catch {
              handleAuthExpired('AUTH-001');
            }
          }
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
            if (item.event === 'deployment' && item.data) {
              try {
                onDeploymentRef.current?.(JSON.parse(item.data) as DeploymentSummary);
              } catch {
                // 忽略单条状态事件解析失败，日志流本身继续读取。
              }
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
