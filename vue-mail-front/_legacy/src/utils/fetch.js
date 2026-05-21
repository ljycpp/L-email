import axios from 'axios';
import { Message } from 'element-ui';
import store from '../store';
import router from '../router';

const service = axios.create({
  baseURL: process.env.BASE_API,
  timeout: 30000
});

service.interceptors.request.use(config => {
  if (store.getters.token) {
    config.headers['X-Token'] = store.getters.token;
  }
  return config;
}, error => {
  console.log(error);
  return Promise.reject(error);
});

service.interceptors.response.use(
  response => {
    const payload = response.data;
    if (payload && typeof payload === 'object' && Object.prototype.hasOwnProperty.call(payload, 'code')) {
      if (payload.code !== 200) {
        if (payload.code === 401) {
          store.dispatch('FedLogOut').then(() => {
            router.push({ path: '/login' });
          });
        }
        Message({
          message: payload.message || 'Request failed',
          type: 'error',
          duration: 5 * 1000
        });
        return Promise.reject(new Error(payload.message || 'Request failed'));
      }
      response.data = payload.data;
    }
    return response;
  },
  error => {
    console.log('err' + error);
    Message({
      message: error.response && error.response.data && error.response.data.message ? error.response.data.message : error.message,
      type: 'error',
      duration: 5 * 1000
    });
    return Promise.reject(error);
  }
);

export default service;
