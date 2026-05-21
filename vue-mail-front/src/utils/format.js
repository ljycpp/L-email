import dayjs from 'dayjs';

export function formatTime(value, pattern = 'YYYY-MM-DD HH:mm') {
  if (!value) {
    return '';
  }
  return dayjs(value).format(pattern);
}

export function isEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

export function formatFileSize(size) {
  const n = Number(size) || 0;
  if (n <= 0) {
    return '';
  }
  if (n < 1024) {
    return `${n} B`;
  }
  if (n < 1024 * 1024) {
    return `${(n / 1024).toFixed(1)} KB`;
  }
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}
