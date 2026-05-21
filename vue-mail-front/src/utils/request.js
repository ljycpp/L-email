import axios from 'axios';
import { ElMessage } from 'element-plus';
import NProgress from 'nprogress';
import router from '@/router';
import { useUserStore } from '@/stores/user';

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 30000
});

service.interceptors.request.use(
  config => {
    NProgress.start();
    const userStore = useUserStore();
    if (userStore.token) {
      config.headers['X-Token'] = userStore.token;
    }
    return config;
  },
  error => {
    NProgress.done();
    return Promise.reject(error);
  }
);

service.interceptors.response.use(
  response => {
    NProgress.done();
    const payload = response.data;
    if (payload && typeof payload === 'object' && Object.prototype.hasOwnProperty.call(payload, 'code')) {
      if (payload.code !== 200) {
        if (payload.code === 401) {
          const userStore = useUserStore();
          userStore.reset();
          router.push('/login');
        }
        const message = payload.message || '请求失败';
        ElMessage.error(message);
        return Promise.reject(new Error(message));
      }
      response.data = payload.data;
    }
    return response;
  },
  error => {
    NProgress.done();
    const message = error.response?.data?.message || error.message || '网络错误';
    ElMessage.error(message);
    return Promise.reject(error);
  }
);

export default service;
