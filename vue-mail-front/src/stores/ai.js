import { defineStore } from 'pinia';

function normalizeMailContext(mail = {}) {
  return {
    id: mail.id ?? null,
    title: mail.title ?? '',
    sender: mail.sender ?? '',
    senderMail: mail.senderMail ?? '',
    mailType: mail.mailType ?? 'receive'
  };
}

export const useAiStore = defineStore('ai', {
  state: () => ({
    visible: false,
    currentMail: normalizeMailContext(),
    sessionKey: 0,
    pendingAction: null
  }),
  actions: {
    openForMail(mail, options = {}) {
      const nextMail = normalizeMailContext(mail);
      const changed = this.currentMail.id !== nextMail.id || this.pendingAction !== (options.action || null);
      this.currentMail = nextMail;
      this.pendingAction = options.action || null;
      this.visible = true;
      if (changed) {
        this.sessionKey += 1;
      }
    },
    close() {
      this.visible = false;
    },
    clearContext() {
      this.currentMail = normalizeMailContext();
      this.visible = false;
      this.pendingAction = null;
      this.sessionKey += 1;
    },
    consumePendingAction() {
      const action = this.pendingAction;
      this.pendingAction = null;
      return action;
    }
  }
});
