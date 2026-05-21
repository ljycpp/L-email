import request from '@/utils/request';

export const spamFilterApi = {
  status: () => request.get('/mail/spam/auto-filter/status'),
  enable: () => request.post('/mail/spam/auto-filter/enable'),
  disable: () => request.post('/mail/spam/auto-filter/disable')
};
