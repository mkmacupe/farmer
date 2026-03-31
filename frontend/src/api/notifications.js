import { API_BASE, apiFetch, authHeaders, readErrorMessage } from './core.js';

export function subscribeNotifications(token, { onNotification, onError } = {}) {
  const controller = new AbortController();
  let closed = false;
  let reconnectAttempts = 0;
  let reconnectTimerId = null;

  const clearReconnectTimer = () => {
    if (reconnectTimerId !== null) {
      window.clearTimeout(reconnectTimerId);
      reconnectTimerId = null;
    }
  };

  const scheduleReconnect = () => {
    if (closed || controller.signal.aborted) {
      return;
    }
    clearReconnectTimer();
    const delayMs = Math.min(10_000, 1_500 * (reconnectAttempts + 1));
    reconnectAttempts += 1;
    reconnectTimerId = window.setTimeout(() => {
      reconnectTimerId = null;
      if (!closed && !controller.signal.aborted) {
        connect();
      }
    }, delayMs);
  };

  const connect = async () => {
    try {
      const response = await apiFetch(`${API_BASE}/notifications/stream`, {
        headers: {
          Accept: 'text/event-stream',
          ...authHeaders(token),
        },
        signal: controller.signal,
        timeoutMs: 0,
      });

      if (!response.ok) {
        throw new Error(await readErrorMessage(response));
      }
      if (!response.body) {
        throw new Error('Поток уведомлений не поддерживается в этом браузере');
      }

      reconnectAttempts = 0;

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let eventName = 'message';
      let dataLines = [];

      const flushEvent = () => {
        const currentEventName = eventName;
        const currentData = dataLines.join('\n');
        eventName = 'message';
        dataLines = [];

        if (currentEventName !== 'notification' || !currentData) {
          return;
        }

        try {
          const payload = JSON.parse(currentData);
          if (onNotification) {
            onNotification(payload);
          }
        } catch {
          // Ignore malformed payload and keep stream alive.
        }
      };

      while (!closed) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split(/\r?\n/);
        buffer = lines.pop();

        for (const line of lines) {
          if (line === '') {
            flushEvent();
            continue;
          }
          if (line.startsWith(':')) {
            continue;
          }
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim() || 'message';
            continue;
          }
          if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).trimStart());
          }
        }
      }

      if (!closed && !controller.signal.aborted) {
        scheduleReconnect();
      }
    } catch (error) {
      if (controller.signal.aborted || closed) {
        return;
      }
      if (onError) {
        onError(error);
      }
      scheduleReconnect();
    }
  };

  connect();

  return () => {
    closed = true;
    clearReconnectTimer();
    controller.abort();
  };
}
