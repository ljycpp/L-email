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

export function buildMailSocketUrl(token) {
  const apiOrigin = resolveApiOrigin();
  const wsProtocol = apiOrigin.startsWith('https://') ? 'wss://' : 'ws://';
  const host = apiOrigin.replace(/^https?:\/\//, '');
  return `${wsProtocol}${host}/ws/mail?token=${encodeURIComponent(token)}`;
}
