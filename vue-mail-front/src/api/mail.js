import request from '@/utils/request';

export const inboxApi = {
  list: params => request.get('/inbox/list', { params }),
  delete: ids => request.post('/mail/delete', { ids }),
  unreadCount: () => request.get('/inbox/unread_count'),
  markAllRead: () => request.post('/inbox/mark_all_read')
};

export const spamApi = {
  list: params => request.get('/spam/list', { params }),
  count: () => request.get('/spam/count')
};

export const mailActionApi = {
  reportSpam: ids => request.post('/mail/report_spam', { ids }),
  notSpam: ids => request.post('/mail/not_spam', { ids })
};

export const outboxApi = {
  list: params => request.get('/outbox/list', { params }),
  delete: ids => request.post('/mail/delete', { ids })
};

export const draftApi = {
  list: params => request.get('/draftbox/list', { params }),
  delete: ids => request.post('/mail/delete', { ids })
};

export const mailListApi = {
  list: params => request.get('/mail_list', { params }),
  delete: ids => request.post('/mail/delete', { ids }),
  restore: ids => request.post('/mail/restore', { ids }),
  deletePermanently: ids => request.post('/mail/delete_permanently', { ids })
};

export const mailDetailApi = {
  get: params => request.get('/mail_detail', { params }),
  delete: ids => request.post('/mail/delete', { ids })
};

export const mailSendApi = {
  send: formData => request.post('/mail_send/send', formData),
  draft: formData => request.post('/mail_send/draft', formData)
};

export const labelApi = {
  list: () => request.get('/mail_label/list'),
  add: data => request.post('/api/labels', data),
  edit: data => request.put(`/api/labels/${data.id}`, data),
  remove: id => request.delete(`/api/labels/${id}`),
  toggleStar: ids => request.post('/mail_label/toggle_star', { ids }),
  mark: (labelId, mailIds) => request.post('/mail_label/mark', { labelId: Number(labelId), mailIds })
};

export const contactApi = {
  list: params => request.get('/mail_contacts/list', { params }),
  add: data => request.post('/api/contacts', data),
  edit: data => request.put(`/api/contacts/${data.id}`, data),
  remove: ids => request.delete('/api/contacts', { data: { ids } })
};

export const groupApi = {
  list: () => request.get('/mail_group/list'),
  contacts: groupId => request.get(`/api/contact-groups/${groupId}/contacts`),
  add: data => request.post('/api/contact-groups', data),
  edit: data => request.put(`/api/contact-groups/${data.id}`, data),
  remove: id => request.delete(`/api/contact-groups/${id}`)
};

export const attachmentApi = {
  uploadUrl: '/api/attachments/upload'
};
