import request from '@/utils/request';

function resolveApiOrigin() {
  const explicitBase = import.meta.env.VITE_API_BASE;
  if (explicitBase) {
    try {
      return new URL(explicitBase, window.location.origin).origin;
    } catch {
      // ignore and fall back
    }
  }

  const { protocol, hostname, port } = window.location;
  if ((hostname === 'localhost' || hostname === '127.0.0.1') && port === '8081') {
    return `${protocol}//${hostname}:${port}`;
  }
  return `${protocol}//${hostname}${port ? `:${port}` : ''}`;
}

/** 先换取一次性 ticket，再建立 WebSocket（URL 中不再携带 JWT） */
export async function buildMailSocketUrl() {
  const { data: ticket } = await request.get('/api/ws/ticket');
  const apiOrigin = resolveApiOrigin();
  const wsProtocol = apiOrigin.startsWith('https://') ? 'wss://' : 'ws://';
  const host = apiOrigin.replace(/^https?:\/\//, '');
  return `${wsProtocol}${host}/ws/mail?ticket=${encodeURIComponent(ticket)}`;
}
