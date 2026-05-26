import { createRouter, createWebHistory } from 'vue-router';
import NProgress from 'nprogress';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';

const MainLayout = () => import('@/layouts/MainLayout.vue');
const LoginView = () => import('@/views/login/LoginView.vue');

export const constantRoutes = [
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/register', component: () => import('@/views/login/RegisterView.vue'), meta: { public: true } },
  { path: '/404', component: () => import('@/views/error/NotFoundView.vue'), meta: { public: true } },
  {
    path: '/',
    component: MainLayout,
    redirect: '/inbox',
    children: [
      { path: '', redirect: '/inbox' },
      { path: 'dashboard', redirect: '/inbox' },
      { path: 'mail_send', name: 'Compose', component: () => import('@/views/mail/ComposeView.vue'), meta: { title: '写邮件', icon: 'EditPen' } },
      { path: 'inbox', name: 'Inbox', component: () => import('@/views/mail/InboxView.vue'), meta: { title: '收件箱', icon: 'Message' } },
      { path: 'star', name: 'Star', component: () => import('@/views/mail/StarView.vue'), meta: { title: '星标邮件', icon: 'Star' } },
      { path: 'spam', name: 'Spam', component: () => import('@/views/mail/SpamView.vue'), meta: { title: '垃圾邮件', icon: 'Warning' } },
      { path: 'outbox', name: 'Outbox', component: () => import('@/views/mail/OutboxView.vue'), meta: { title: '发件箱', icon: 'Promotion' } },
      { path: 'draftbox', name: 'Drafts', component: () => import('@/views/mail/DraftView.vue'), meta: { title: '草稿箱', icon: 'Document' } },
      {
        path: 'mail_list',
        name: 'MailList',
        component: () => import('@/views/mail/MailListRouterView.vue'),
        meta: { title: '回收站', icon: 'Delete', isDeleted: true }
      },
      { path: 'mail_detail', name: 'MailDetail', component: () => import('@/views/mail/DetailView.vue'), meta: { title: '邮件详情', hidden: true } },
      { path: 'settings', name: 'Settings', component: () => import('@/views/ai/AiSettingsView.vue'), meta: { title: '设置', icon: 'Setting' } },
      { path: 'ai-settings', redirect: '/settings' },
      { path: 'calendar', name: 'Calendar', component: () => import('@/views/calendar/CalendarView.vue'), meta: { title: '日历', icon: 'Calendar' } },
      { path: 'mail_label', name: 'Labels', component: () => import('@/views/label/LabelView.vue'), meta: { title: '标签管理', icon: 'CollectionTag', group: 'labels' } },
      { path: 'mail_contacts', name: 'Contacts', component: () => import('@/views/contact/ContactView.vue'), meta: { title: '联系人', icon: 'User', group: 'contacts' } },
      { path: 'mail_contacts/group', name: 'Groups', component: () => import('@/views/contact/GroupView.vue'), meta: { title: '分组管理', icon: 'Folder', group: 'contacts' } },
      { path: 'mail_contacts/group_members', name: 'GroupMembers', component: () => import('@/views/contact/GroupMembersView.vue'), meta: { title: '分组成员', icon: 'UserFilled', group: 'contacts' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/404' }
];

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 })
});

router.beforeEach(async (to, from, next) => {
  NProgress.start();
  const userStore = useUserStore();

  if (to.meta.public) {
    if (to.path === '/login' && userStore.token) {
      next('/inbox');
      return;
    }
    next();
    return;
  }

  if (!userStore.token) {
    next('/login');
    return;
  }

  try {
    if (!userStore.name) {
      await userStore.fetchProfile();
    }
    await useMenuStore().loadMenus();
    next();
  } catch {
    userStore.reset();
    next('/login');
  }
});

router.afterEach(() => {
  NProgress.done();
});

export default router;
