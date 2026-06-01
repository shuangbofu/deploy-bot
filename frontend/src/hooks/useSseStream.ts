import { useEffect, useRef } from 'react';
import { API_BASE_URL, handleAuthExpired } from '../api/client';
import { authStorage } from '../auth/authStorage';

type Options<T> = {
  enabled: boolean;
  path: string;
  eventName: string;
  onData: (payload: T) => void;
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
    if (dataLines.length > 0) {
      events.push({ event, data: dataLines.join('\n') });
    }
    splitIndex = nextBuffer.indexOf('\n\n');
  }
  return { events, buffer: nextBuffer };
};

const resolveStreamUrl = (path: string) => {
  const nextPath = path.startsWith('/') ? `${API_BASE_URL}${path}` : `${API_BASE_URL}/${path}`;
  if (/^https?:\/\//i.test(nextPath)) {
    return nextPath;
  }
  return new URL(nextPath, window.location.origin).toString();
};

export function useSseStream<T>({ enabled, path, eventName, onData, onError }: Options<T>) {
  const onDataRef = useRef(onData);
  const onErrorRef = useRef(onError);

  useEffect(() => {
    onDataRef.current = onData;
    onErrorRef.current = onError;
  }, [onData, onError]);

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
        const response = await fetch(resolveStreamUrl(path), {
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
          throw new Error('状态流连接失败');
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
            if (item.event === eventName && item.data) {
              onDataRef.current(JSON.parse(item.data) as T);
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
  }, [enabled, eventName, path]);
}
