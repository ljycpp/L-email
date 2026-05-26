import request from '@/utils/request';

export function loginByEmail(email, password) {
  return request.post('/login/loginbyemail', { email, password });
}

export function register(email, password, nickname) {
  return request.post('/api/auth/register', { email, password, nickname });
}

export function logout() {
  return request.post('/login/logout');
}

export function getInfo(token) {
  return request.get('/user/info', { params: { token } });
}

export function updateInfo(data) {
  return request.put('/user/info', data);
}
