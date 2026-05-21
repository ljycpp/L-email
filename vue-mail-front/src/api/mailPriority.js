import request from '@/utils/request';

export const mailPriorityApi = {
  status: () => request.get('/mail/priority/auto-filter/status'),
  enable: () => request.post('/mail/priority/auto-filter/enable'),
  disable: () => request.post('/mail/priority/auto-filter/disable')
};
