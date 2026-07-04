import { useUserStore } from '@/stores/user';
import { resolveApiUrl } from '@/utils/url';

function resolveAttachmentPath(file) {
  return file?.url || (file?.id ? `/api/attachments/${file.id}/download` : '');
}

/** 通过 Header 携带 Token 下载，避免 token 出现在 URL 中 */
export async function downloadAttachment(file) {
  const path = resolveAttachmentPath(file);
  if (!path) {
    return;
  }
  const url = resolveApiUrl(path);
  const userStore = useUserStore();
  const headers = {};
  if (userStore.token) {
    headers['X-Token'] = userStore.token;
  }
  const response = await fetch(url, { headers, credentials: 'include' });
  if (!response.ok) {
    throw new Error('附件下载失败');
  }
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = file?.name || 'attachment';
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}

/** 仅返回下载地址，不再附加 query token */
export function buildAttachmentHref(file) {
  const path = resolveAttachmentPath(file);
  if (!path) {
    return '#';
  }
  return resolveApiUrl(path);
}
