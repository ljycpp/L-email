import { defineStore } from 'pinia';

const TOAST_TTL_MS = 10000;
const MAX_TOASTS = 5;

let toastSeq = 0;

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    inboxTick: 0,
    spamTick: 0,
    highPriorityTick: 0,
    lastNewMail: null,
    toasts: []
  }),
  actions: {
    onNewMail(payload) {
      this.lastNewMail = payload;
      this.inboxTick += 1;
      if (payload?.priorityLevel === 'HIGH') {
        this.highPriorityTick += 1;
      }
      this.pushToast({ ...payload, kind: payload?.priorityLevel === 'HIGH' ? 'priority' : 'new' });
    },
    onSpamFiltered(_payload) {
      // 自动识别为垃圾邮件：仅刷新侧栏/列表计数，不弹出右下角 Toast
      this.spamTick += 1;
    },
    pushToast(payload) {
      const id = ++toastSeq;
      this.toasts.push({
        id,
        kind: payload?.kind || 'new',
        mailId: payload?.mailId ?? null,
        sender: payload?.sender || '未知发件人',
        title: payload?.title || '（无主题）',
        senderMail: payload?.senderMail || '',
        reason: payload?.reason || '',
        priorityLevel: payload?.priorityLevel || '',
        priorityScore: payload?.priorityScore ?? null
      });
      if (this.toasts.length > MAX_TOASTS) {
        this.toasts.splice(0, this.toasts.length - MAX_TOASTS);
      }
      window.setTimeout(() => this.removeToast(id), TOAST_TTL_MS);
    },
    removeToast(id) {
      const idx = this.toasts.findIndex(t => t.id === id);
      if (idx >= 0) {
        this.toasts.splice(idx, 1);
      }
    },
    clearAll() {
      this.toasts = [];
      this.lastNewMail = null;
      this.spamTick = 0;
      this.highPriorityTick = 0;
    }
  }
});
