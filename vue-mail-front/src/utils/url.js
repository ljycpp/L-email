export function resolveApiUrl(url) {
  if (!url) {
    return url;
  }
  if (/^https?:\/\//i.test(url) || /^blob:/i.test(url) || /^data:/i.test(url)) {
    return url;
  }
  const base = import.meta.env.VITE_API_BASE || '';
  if (url.charAt(0) === '/') {
    return `${base}${url}`;
  }
  return `${base}/${url}`;
}
