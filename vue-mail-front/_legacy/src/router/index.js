import Vue from 'vue';
import Router from 'vue-router';

import Layout from '../views/layout/Layout';
import Login from '../views/login/';
import * as labelAPI from 'api/mail_label';
import * as groupAPI from 'api/mail_group';

const authRedirect = () => import('../views/login/authredirect');
const dashboard = () => import('../views/dashboard/index');
const Introduction = () => import('../views/introduction/index');
const Err404 = () => import('../views/error/404');
const Err401 = () => import('../views/error/401');
const ErrorLog = () => import('../views/errlog/index');
const Inbox = () => import('../views/inbox/index');
const Outbox = () => import('../views/outbox/index');
const DraftBox = () => import('../views/draftbox/index');
const MailSend = () => import('../views/mail_send/index');
const MailDetail = () => import('../views/mail_detail/index');
const MailLabel = () => import('../views/mail_label/index');
const MailList = () => import('../views/mail_list/index');
const MailGroup = () => import('../views/mail_contacts/group');
const ContactList = () => import('../views/mail_contacts/index');

Vue.use(Router);

export const constantRouterMap = [
  { path: '/login', component: Login, hidden: true },
  { path: '/authredirect', component: authRedirect, hidden: true },
  { path: '/sendpwd', redirect: '/login', hidden: true },
  { path: '/reset', redirect: '/login', hidden: true },
  { path: '/404', component: Err404, hidden: true },
  { path: '/401', component: Err401, hidden: true },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    name: 'Home',
    hidden: true,
    children: [{ path: 'dashboard', component: dashboard }]
  },
  {
    path: '/introduction',
    component: Layout,
    redirect: '/introduction/index',
    icon: 'question2',
    noDropdown: true,
    children: [{ path: 'index', component: Introduction, name: 'Intro' }]
  },
  {
    path: '/mail_send',
    component: Layout,
    redirect: '/mail_send/index',
    icon: 'edit2',
    noDropdown: true,
    children: [{ path: 'index', component: MailSend, name: 'Compose' }]
  },
  {
    path: '/inbox',
    component: Layout,
    redirect: '/inbox/index',
    icon: 'inbox',
    noDropdown: true,
    children: [{ path: 'index', component: Inbox, name: 'Inbox' }]
  },
  {
    path: '/outbox',
    component: Layout,
    redirect: '/outbox/index',
    icon: 'outbox',
    noDropdown: true,
    children: [{ path: 'index', component: Outbox, name: 'Outbox' }]
  },
  {
    path: '/draftbox',
    component: Layout,
    redirect: '/draftbox/index',
    icon: 'edit3',
    noDropdown: true,
    children: [{ path: 'index', component: DraftBox, name: 'Drafts' }]
  },
  {
    path: '/mail_list',
    component: Layout,
    redirect: '/mail_list/index',
    icon: 'recycle3',
    noDropdown: true,
    children: [
      { path: 'index', component: MailList, name: 'Trash', meta: { isDeleted: true } },
      { path: 'index/:labelId', component: MailList, name: 'LabelMailList', hidden: true }
    ]
  },
  {
    path: '/mail_detail',
    component: Layout,
    redirect: '/mail_detail/index',
    hidden: true,
    children: [{ path: 'index/:mailId?', component: MailDetail, name: 'MailDetail' }]
  },
  {
    path: '/mail_contacts',
    component: Layout,
    redirect: '/mail_contacts/index',
    hidden: true,
    children: [{ path: 'index/:groupId?', component: ContactList, name: 'ContactList' }]
  },
  {
    path: '/mail_label',
    component: Layout,
    redirect: '/mail_label/index',
    icon: 'xinrenzhinan',
    hidden: true,
    children: [{ path: 'index', component: MailLabel }]
  }
];

export default new Router({
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRouterMap
});

export const asyncRouterMap = [
  {
    path: '',
    component: Layout,
    redirect: 'noredirect',
    name: 'Labels',
    icon: 'label7',
    children: [{ path: 'mail_label/index', component: MailLabel, name: 'LabelManage' }]
  },
  {
    path: '',
    component: Layout,
    redirect: 'noredirect',
    name: 'Contacts',
    icon: 'contact5',
    children: [
      { path: 'mail_contacts/group', component: MailGroup, name: 'GroupManage' },
      { path: 'mail_contacts/index', component: ContactList, name: 'AllContacts' }
    ]
  },
  {
    path: '/errorpage',
    component: Layout,
    redirect: 'noredirect',
    name: 'ErrorPages',
    icon: 'warn1',
    children: [
      { path: '401', component: Err401, name: '401' },
      { path: '404', component: Err404, name: '404' }
    ]
  },
  {
    path: '/errlog',
    component: Layout,
    redirect: 'noredirect',
    name: 'errlog',
    icon: 'bug',
    hidden: true,
    noDropdown: true,
    children: [{ path: 'log', component: ErrorLog, name: 'ErrorLog' }]
  },
  { path: '*', redirect: '/404', hidden: true }
];

let dynamicRoutesLoaded = false;

export function loadDynamicRoutes() {
  if (dynamicRoutesLoaded) {
    return Promise.resolve(asyncRouterMap);
  }

  return Promise.all([labelAPI.fetchList(), groupAPI.fetchList()]).then(([labelRes, groupRes]) => {
    const labelList = labelRes.data.labelList || [];
    const groupList = groupRes.data.groupList || [];
    const labelMenu = asyncRouterMap.find(item => item.name === 'Labels');
    const groupMenu = asyncRouterMap.find(item => item.name === 'Contacts');

    labelList.forEach(item => {
      const exists = labelMenu.children.find(child => child.meta && child.meta.labelId === item.id);
      if (!exists) {
        labelMenu.children.push({
          path: 'mail_list/index/' + item.id,
          component: MailList,
          name: item.name,
          meta: { labelId: item.id }
        });
      }
    });

    groupList.forEach(item => {
      const exists = groupMenu.children.find(child => child.meta && child.meta.groupId === item.id);
      if (!exists) {
        groupMenu.children.push({
          path: 'mail_contacts/index/' + item.id,
          component: ContactList,
          name: item.name,
          meta: { groupId: item.id }
        });
      }
    });

    dynamicRoutesLoaded = true;
    return asyncRouterMap;
  });
}
