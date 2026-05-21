import { useUserStore } from '@/stores/user';
import { resolveApiUrl } from '@/utils/url';

export function buildAttachmentHref(file) {
  const path = file?.url || (file?.id ? `/api/attachments/${file.id}/download` : '');
  if (!path) {
    return '#';
  }
  const url = resolveApiUrl(path);
  const userStore = useUserStore();
  if (!userStore.token) {
    return url;
  }
  const joiner = url.includes('?') ? '&' : '?';
  return `${url}${joiner}token=${encodeURIComponent(userStore.token)}`;
}
