import { useEffect, useRef } from 'react';
import { API_BASE_URL, handleAuthExpired } from '../api/client';
import { authStorage } from '../auth/authStorage';
import type { PipelineHallSummary } from '../types/domain';

type HallStreamPayload = {
  version?: number;
  full?: boolean;
  items?: PipelineHallSummary[];
};

type Options = {
  enabled: boolean;
  version?: number;
  onUpdate: (payload: Required<Pick<HallStreamPayload, 'version'>> & Pick<HallStreamPayload, 'full' | 'items'>) => void;
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

const resolveStreamUrl = (version?: number) => {
  const params = version == null ? '' : `?version=${version}`;
  const path = `${API_BASE_URL}/pipelines/hall/stream${params}`;
  if (/^https?:\/\//i.test(path)) {
    return path;
  }
  return new URL(path, window.location.origin).toString();
};

export function usePipelineHallStream({ enabled, version, onUpdate, onError }: Options) {
  const onUpdateRef = useRef(onUpdate);
  const onErrorRef = useRef(onError);
  const versionRef = useRef(version);

  useEffect(() => {
    onUpdateRef.current = onUpdate;
    onErrorRef.current = onError;
    versionRef.current = version;
  }, [onError, onUpdate, version]);

  useEffect(() => {
    if (!enabled) {
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
        const response = await fetch(resolveStreamUrl(versionRef.current), {
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
          throw new Error('流水线大厅状态流连接失败');
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
            if (item.event !== 'hall' || !item.data) {
              return;
            }
            const payload = JSON.parse(item.data) as HallStreamPayload;
            if (typeof payload.version === 'number') {
              onUpdateRef.current(payload as Required<Pick<HallStreamPayload, 'version'>> & Pick<HallStreamPayload, 'full' | 'items'>);
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
  }, [enabled]);
}
