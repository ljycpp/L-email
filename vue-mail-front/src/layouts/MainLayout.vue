<template>
  <el-container class="layout-root">
    <el-header class="layout-header">
      <div class="header-left">
        <el-button
          class="sidebar-toggle-btn"
          text
          circle
          @click="appStore.toggleSidebar"
        >
          <el-icon :size="20">
            <PanelLeftIcon />
          </el-icon>
        </el-button>
        <div class="header-logo" @click="router.push('/inbox')">
          <span class="logo-text">{{ MAIL_SYSTEM_NAME }}</span>
        </div>
      </div>
      <div class="header-search">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索邮件主题或正文"
          clearable
          :prefix-icon="Search"
          @keyup.enter="onSearch"
        />
      </div>
      <div class="header-right">
        <el-avatar :size="32" :src="userStore.avatar">{{ avatarLetter }}</el-avatar>
        <div class="user-meta">
          <span class="user-name">{{ userStore.name || '用户' }}</span>
          <span v-if="userStore.email" class="user-email">{{ userStore.email }}</span>
        </div>
        <el-button type="danger" link @click="handleLogout">退出</el-button>
      </div>
    </el-header>

    <el-container class="layout-body">
      <el-aside :width="appStore.sidebarCollapsed ? '88px' : '240px'" class="layout-aside" :class="{ 'is-collapsed': appStore.sidebarCollapsed }">
        <el-button
          v-if="!appStore.sidebarCollapsed"
          type="primary"
          class="compose-btn"
          @click="router.push('/mail_send')"
        >
          <el-icon><SquarePenIcon /></el-icon>
          写邮件
        </el-button>
        <el-tooltip v-else content="写邮件" placement="right" effect="dark">
          <el-button type="primary" class="compose-btn-mini" circle @click="router.push('/mail_send')">
            <el-icon><SquarePenIcon /></el-icon>
          </el-button>
        </el-tooltip>

        <el-menu
          :default-active="activeMenu"
          :collapse="appStore.sidebarCollapsed"
          :collapse-transition="false"
          popper-effect="dark"
          router
          class="sidebar-menu"
        >
          <el-menu-item index="/inbox">
            <el-icon><InboxIcon /></el-icon>
            <template #title>
              <span>收件箱</span>
              <span v-if="unreadCount > 0 && !appStore.sidebarCollapsed" class="menu-badge">{{ unreadCount }}</span>
            </template>
          </el-menu-item>
          <el-menu-item index="/star">
            <el-icon><StarIcon /></el-icon>
            <template #title>星标邮件</template>
          </el-menu-item>
          <el-menu-item index="/outbox">
            <el-icon><SendIcon /></el-icon>
            <template #title>发件箱</template>
          </el-menu-item>
          <el-menu-item index="/draftbox">
            <el-icon><FileTextIcon /></el-icon>
            <template #title>草稿箱</template>
          </el-menu-item>
          <el-menu-item index="/mail_list">
            <el-icon><Trash2Icon /></el-icon>
            <template #title>回收站</template>
          </el-menu-item>
          <el-menu-item index="/spam">
            <el-icon><OctagonAlertIcon /></el-icon>
            <template #title>
              <span>垃圾邮件</span>
              <span v-if="spamCount > 0 && !appStore.sidebarCollapsed" class="menu-badge spam">{{ spamCount }}</span>
            </template>
          </el-menu-item>
          <el-menu-item index="/mail_label">
            <el-icon><FolderIcon /></el-icon>
            <template #title>标签</template>
          </el-menu-item>
          <el-menu-item index="/mail_contacts">
            <el-icon><UserRoundIcon /></el-icon>
            <template #title>联系人</template>
          </el-menu-item>
          <el-menu-item index="/calendar">
            <el-icon><CalendarDaysIcon /></el-icon>
            <template #title>日历</template>
          </el-menu-item>
          <el-menu-item index="/settings">
            <el-icon><SettingsIcon /></el-icon>
            <template #title>设置</template>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <router-view @stats-change="loadSidebarCounts" />
      </el-main>

      <div class="layout-ai-aside" :class="{ 'is-visible': aiStore.visible }">
        <AiAssistantDrawer />
      </div>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Calendar as CalendarDaysIcon,
  Delete as Trash2Icon,
  Document as FileTextIcon,
  EditPen as SquarePenIcon,
  Fold as PanelLeftIcon,
  Folder as FolderIcon,
  MessageBox as InboxIcon,
  Promotion as SendIcon,
  Search,
  Setting as SettingsIcon,
  Star as StarIcon,
  User as UserRoundIcon,
  Warning as OctagonAlertIcon
} from '@element-plus/icons-vue';
import { useAppStore } from '@/stores/app';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';
import { useAiStore } from '@/stores/ai';
import { useNotificationStore } from '@/stores/notification';
import { inboxApi, spamApi } from '@/api/mail';
import { MAIL_SYSTEM_NAME } from '@/constants/brand';
import AiAssistantDrawer from '@/components/AiAssistantDrawer.vue';
import { disconnectMailWebSocket } from '@/composables/useMailWebSocket';

const route = useRoute();
const router = useRouter();
const appStore = useAppStore();
const userStore = useUserStore();
const menuStore = useMenuStore();
const aiStore = useAiStore();
const notificationStore = useNotificationStore();

const searchKeyword = ref('');
const unreadCount = ref(0);
const spamCount = ref(0);

const activeMenu = computed(() => {
  if (route.fullPath.includes('/mail_contacts/group_members')) {
    return '/mail_contacts';
  }
  if (route.path.startsWith('/mail_list') && route.query.labelId) {
    return '/mail_label';
  }
  if (route.path.startsWith('/mail_contacts')) {
    return '/mail_contacts';
  }
  return route.path;
});

const avatarLetter = computed(() => (userStore.name || '用户').charAt(0));

async function loadSidebarCounts() {
  try {
    const [{ data: unread }, { data: spam }] = await Promise.all([
      inboxApi.unreadCount(),
      spamApi.count()
    ]);
    unreadCount.value = unread?.count ?? 0;
    spamCount.value = spam?.count ?? 0;
  } catch {
    unreadCount.value = 0;
    spamCount.value = 0;
  }
}

onMounted(loadSidebarCounts);
watch(() => route.path, loadSidebarCounts);
watch(() => notificationStore.inboxTick, loadSidebarCounts);
watch(() => notificationStore.readTick, loadSidebarCounts);
watch(() => notificationStore.spamTick, loadSidebarCounts);

function onSearch() {
  const query = searchKeyword.value?.trim() ? { title: searchKeyword.value.trim() } : {};
  if (route.path !== '/inbox') {
    router.push({ path: '/inbox', query });
    return;
  }
  router.replace({ path: '/inbox', query });
}

watch(
  () => route.query.title,
  title => {
    searchKeyword.value = title ? String(title) : '';
  },
  { immediate: true }
);

async function handleLogout() {
  disconnectMailWebSocket();
  notificationStore.clearAll();
  menuStore.loaded = false;
  try {
    await userStore.logout();
  } catch {
    userStore.reset();
  }
  await router.replace('/login');
}
</script>

<style scoped lang="scss">
.layout-root { height: 100vh; flex-direction: column; background: var(--color-bg-page); }
.layout-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 18px;
  background: var(--color-bg-page);
  border-bottom: 1px solid var(--color-border);
  height: 70px;
  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
    width: 222px;
  }
  .sidebar-toggle-btn {
    color: var(--color-text-secondary);
    :deep(svg) {
      width: 19px;
      height: 19px;
      stroke-width: 1.8;
    }
    &:hover {
      background-color: var(--color-hover);
      color: var(--color-primary);
    }
  }
}
.header-logo { cursor: pointer; flex-shrink: 0; display: flex; align-items: center; }
.logo-text { font-size: 22px; font-weight: 700; color: var(--color-text-main); font-family: 'Google Sans', Roboto, Arial, sans-serif; letter-spacing: 0; }

.header-search {
  flex: 1;
  max-width: 680px;
  margin-left: 4px;
  :deep(.el-input__wrapper) {
    background-color: #f1f4f9;
    border-radius: 24px;
    box-shadow: none !important;
    border: 1px solid transparent;
    padding: 0 18px;
    height: 46px;
    transition: background-color 0.2s, border-color 0.2s, box-shadow 0.2s;
    &:hover {
      background-color: var(--color-hover);
    }
    &.is-focus {
      background-color: var(--color-bg-card);
      border-color: #dde3f0;
      box-shadow: 0 4px 16px rgba(31, 41, 55, 0.08) !important;
    }
  }
  :deep(.el-input__inner) {
    font-size: 15px;
    color: var(--color-text-main);
    &::placeholder {
      color: var(--color-text-muted);
    }
  }
}

.header-right { display: flex; align-items: center; gap: 16px; margin-left: auto; }
.user-meta { display: flex; flex-direction: column; line-height: 1.2; }
.user-name { font-size: 13px; font-weight: 600; color: var(--color-text-main); }
.user-email { font-size: 11px; color: var(--color-text-secondary); }
.layout-body { flex: 1; overflow: hidden; }

.layout-aside {
  display: flex;
  flex-direction: column;
  background: var(--color-bg-sidebar);
  border-right: none;
  overflow: hidden;
  transition: width 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.layout-aside.is-collapsed {
  align-items: center;
}

.compose-btn {
  margin: 16px 14px 12px;
  height: 48px;
  border-radius: 16px;
  font-weight: 600;
  font-size: 14px;
  background: var(--color-primary-light) !important;
  border: none !important;
  color: var(--color-primary) !important;
  box-shadow: none !important;
  transition: background-color 0.2s, color 0.2s;
  &:hover {
    background: #dde7ff !important;
    color: var(--color-primary-hover) !important;
  }
  .el-icon {
    font-size: 18px;
    margin-right: 8px;
    :deep(svg) {
      width: 18px;
      height: 18px;
      stroke-width: 1.8;
    }
  }
}

.compose-btn-mini {
  margin: 16px 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: var(--color-primary-light) !important;
  border: none !important;
  color: var(--color-primary) !important;
  box-shadow: none !important;
  transition: background-color 0.2s, color 0.2s;
  &:hover {
    background: #dde7ff !important;
    color: var(--color-primary-hover) !important;
  }
  .el-icon {
    font-size: 18px;
    :deep(svg) {
      width: 18px;
      height: 18px;
      stroke-width: 1.8;
    }
  }
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
  background: transparent;
  width: 100%;
  padding: 0 10px 14px 0;

  &.el-menu--collapse {
    width: 88px;
    --el-menu-collapse-width: 88px;
    padding: 0 16px 14px;
    display: flex;
    align-items: center;
    flex-direction: column;

    :deep(.el-menu-item) {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      min-width: 56px;
      height: 48px;
      line-height: 48px;
      margin: 0 0 8px;
      padding: 0 !important;
      border-radius: 16px;
    }

    :deep(.el-menu-item .el-icon) {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      margin: 0 !important;
      transform: none;
    }

    :deep(.el-menu-item .el-icon svg) {
      width: 20px;
      height: 20px;
    }
  }
  
  :deep(.el-menu-item) {
    height: 42px;
    line-height: 42px;
    margin: 0 0 4px 12px;
    border-radius: 14px;
    color: var(--color-text-secondary);
    font-size: 15px;
    font-weight: 500;
    padding-left: 14px !important;
    
    .el-icon {
      color: currentColor;
      font-size: 18px;
      svg {
        width: 18px;
        height: 18px;
        stroke-width: 1.8;
      }
    }
    
    &:hover {
      background-color: var(--color-hover);
      color: var(--color-text-main);
    }
    
    &.is-active {
      background-color: var(--color-primary-light);
      color: var(--color-primary);
      font-weight: 600;
      .el-icon {
        color: currentColor;
      }
    }
  }

  :deep(.el-sub-menu) {
    margin-left: 12px;
    
    .el-sub-menu__title {
      height: 42px;
      line-height: 42px;
      border-radius: 14px;
      color: var(--color-text-secondary);
      font-size: 15px;
      font-weight: 500;
      padding-left: 14px !important;
      
      .el-icon {
        color: currentColor;
        font-size: 18px;
        svg {
          width: 18px;
          height: 18px;
          stroke-width: 1.8;
        }
      }
      
      &:hover {
        background-color: var(--color-hover);
        color: var(--color-text-main);
      }
    }
    
    .el-menu {
      background: transparent;
    }
    
    .el-menu-item {
      margin-left: 16px;
    }
  }
}

.menu-badge {
  margin-left: auto;
  min-width: 22px;
  height: 20px;
  font-size: 12px;
  background: #eef2ff;
  color: var(--color-primary);
  padding: 0 6px;
  border-radius: 999px;
  line-height: 20px;
  font-weight: 600;
  text-align: center;
}
.menu-badge.spam { background: #fff1f2; color: #e11d48; }
.folder-item-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
}
.folder-add-row { display: flex; align-items: center; gap: 4px; color: var(--color-primary); }
.layout-main { padding: 0; overflow: auto; background: var(--color-bg-card); border-radius: 16px 0 0 0; margin-right: 0; border: 1px solid var(--color-border); border-right: 0; border-bottom: 0; box-shadow: var(--shadow-soft); }

.layout-ai-aside {
  width: 380px;
  height: 100%;
  background: var(--color-bg-card);
  border-left: 1px solid var(--color-border);
  transition: width 0.2s cubic-bezier(0.4, 0, 0.2, 1), border-left-color 0.2s;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  &.is-collapsed, &:not(.is-visible) {
    width: 0;
    border-left: 1px solid transparent;
  }
}
</style>

