import { defineStore } from 'pinia';
import { labelApi, groupApi } from '@/api/mail';

export const useMenuStore = defineStore('menu', {
  state: () => ({
    labels: [],
    groups: [],
    loaded: false
  }),
  actions: {
    async loadMenus() {
      if (this.loaded) {
        return;
      }
      const [labelRes, groupRes] = await Promise.all([labelApi.list(), groupApi.list()]);
      this.labels = labelRes.data.labelList || [];
      this.groups = groupRes.data.groupList || [];
      this.loaded = true;
    },
    refreshMenus() {
      this.loaded = false;
      return this.loadMenus();
    },
    async reloadMenus() {
      const [labelRes, groupRes] = await Promise.all([labelApi.list(), groupApi.list()]);
      this.labels = labelRes.data.labelList || [];
      this.groups = groupRes.data.groupList || [];
      this.loaded = true;
    }
  }
});
