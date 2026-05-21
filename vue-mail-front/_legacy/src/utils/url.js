export function resolveApiUrl(url) {
  if (!url) {
    return url;
  }
  if (/^https?:\/\//i.test(url) || /^blob:/i.test(url) || /^data:/i.test(url)) {
    return url;
  }
  if (url.charAt(0) === '/') {
    return `${process.env.BASE_API}${url}`;
  }
  return `${process.env.BASE_API}/${url}`;
}
