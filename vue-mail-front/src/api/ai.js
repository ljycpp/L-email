import request from '@/utils/request';

export const aiConfigApi = {
  get: () => request.get('/api/ai/config'),
  save: data => request.put('/api/ai/config', data),
  test: data => request.post('/api/ai/config/test', data)
};

export const aiMailApi = {
  summarize: mailId => request.post('/api/ai/mail-summary', { mailId }),
  suggestReplies: (mailId, tone = 'formal') => request.post('/api/ai/reply-suggestions', { mailId, tone }),
  extractActionItems: mailId => request.post('/api/ai/action-items', { mailId }),
  chat: (message, mailId) => request.post('/api/ai/chat', { message, mailId: mailId || null })
};
