import { defineStore } from 'pinia';

export const useMailStore = defineStore('mail', {
  state: () => ({
    mailId: null,
    draftId: null,
    pageType: '',
    mailType: '',
    target: null,
    aiDraftContent: ''
  }),
  actions: {
    setMailId(id) {
      this.mailId = id;
    },
    setDraftId(id) {
      this.draftId = id;
    },
    setPageType(type) {
      this.pageType = type;
    },
    setMailType(type) {
      this.mailType = type;
    },
    setTarget(list) {
      this.target = list ? [...list] : null;
    },
    setAiDraftContent(content) {
      this.aiDraftContent = content || '';
    },
    clearComposeContext() {
      this.mailId = null;
      this.draftId = null;
      this.pageType = '';
      this.mailType = '';
      this.target = null;
      this.aiDraftContent = '';
    }
  }
});
