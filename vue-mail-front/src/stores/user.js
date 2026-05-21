import { defineStore } from 'pinia';
import Cookies from 'js-cookie';
import { loginByEmail, logout, getInfo } from '@/api/auth';

const TOKEN_KEY = 'Admin-Token';

export const useUserStore = defineStore('user', {
  state: () => ({
    token: Cookies.get(TOKEN_KEY) || '',
    name: '',
    email: '',
    avatar: '',
    roles: []
  }),
  actions: {
    async login(email, password) {
      const { data } = await loginByEmail(email, password);
      this.token = data.token;
      Cookies.set(TOKEN_KEY, data.token);
    },
    async fetchProfile() {
      const { data } = await getInfo(this.token);
      this.name = data.name;
      this.email = data.email || '';
      this.avatar = data.avatar;
      this.roles = data.role || ['admin'];
      return data;
    },
    async logout() {
      try {
        await logout();
      } finally {
        this.reset();
      }
    },
    reset() {
      this.token = '';
      this.name = '';
      this.email = '';
      this.avatar = '';
      this.roles = [];
      Cookies.remove(TOKEN_KEY);
    }
  }
});
