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
            <Menu />
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
      <el-aside :width="appStore.sidebarCollapsed ? '72px' : '256px'" class="layout-aside">
        <el-button
          v-if="!appStore.sidebarCollapsed"
          type="primary"
          class="compose-btn"
          @click="router.push('/mail_send')"
        >
          <el-icon><EditPen /></el-icon>
          写邮件
        </el-button>
        <el-tooltip v-else content="写邮件" placement="right" effect="dark">
          <el-button type="primary" class="compose-btn-mini" circle @click="router.push('/mail_send')">
            <el-icon><EditPen /></el-icon>
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
            <el-icon><Message /></el-icon>
            <template #title>
              <span>收件箱</span>
              <span v-if="unreadCount > 0 && !appStore.sidebarCollapsed" class="menu-badge">{{ unreadCount }}</span>
            </template>
          </el-menu-item>
          <el-menu-item index="/star">
            <el-icon><Star /></el-icon>
            <template #title>星标邮件</template>
          </el-menu-item>
          <el-menu-item index="/outbox">
            <el-icon><Promotion /></el-icon>
            <template #title>发件箱</template>
          </el-menu-item>
          <el-menu-item index="/draftbox">
            <el-icon><Document /></el-icon>
            <template #title>草稿箱</template>
          </el-menu-item>
          <el-menu-item index="/mail_list">
            <el-icon><Delete /></el-icon>
            <template #title>回收站</template>
          </el-menu-item>
          <el-menu-item index="/spam">
            <el-icon><Warning /></el-icon>
            <template #title>
              <span>垃圾邮件</span>
              <span v-if="spamCount > 0 && !appStore.sidebarCollapsed" class="menu-badge spam">{{ spamCount }}</span>
            </template>
          </el-menu-item>
          <el-menu-item index="/ai-settings">
            <el-icon><Setting /></el-icon>
            <template #title>AI 设置</template>
          </el-menu-item>

          <el-sub-menu index="labels">
            <template #title>
              <el-icon><Folder /></el-icon>
              <span v-if="!appStore.sidebarCollapsed">标签</span>
            </template>
            <el-menu-item v-for="item in menuStore.labels" :key="item.id" :index="labelMenuIndex(item)">
              <template #title>
                <span class="folder-item-dot" :style="{ background: item.color }" />
                {{ item.name }}
              </template>
            </el-menu-item>
            <el-menu-item index="folder-new">
              <template #title>
                <span class="folder-add-row" @click.stop.prevent="openNewFolderDialog">
                  <el-icon class="folder-add-icon"><Plus /></el-icon>
                  新建标签
                </span>
              </template>
            </el-menu-item>
            <el-menu-item index="/mail_label">管理标签</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="contacts">
            <template #title>
              <el-icon><User /></el-icon>
              <span>联系人</span>
            </template>

            <el-sub-menu index="contacts-items">
              <template #title>联系人</template>
              <el-menu-item index="/mail_contacts?create=1">新建联系人</el-menu-item>
              <el-menu-item index="/mail_contacts">全部联系人</el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="contacts-groups">
              <template #title>分组</template>
              <el-menu-item index="/mail_contacts/group?create=1">新建分组</el-menu-item>
              <el-menu-item index="/mail_contacts/group">分组管理</el-menu-item>
              <el-menu-item
                v-for="item in menuStore.groups"
                :key="item.id"
                :index="`/mail_contacts/group_members?groupId=${item.id}`"
              >
                {{ item.name }}
              </el-menu-item>
            </el-sub-menu>
          </el-sub-menu>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <router-view @stats-change="loadSidebarCounts" />
      </el-main>

      <div class="layout-ai-aside" :class="{ 'is-visible': aiStore.visible }">
        <AiAssistantDrawer />
      </div>
    </el-container>

    <el-dialog v-model="folderDialogVisible" title="新建标签" width="400px" @closed="resetFolderForm">
      <el-form :model="folderForm" label-width="72px" @submit.prevent="submitNewFolder">
        <el-form-item label="名称" required>
          <el-input
            ref="folderNameInputRef"
            v-model="folderForm.name"
            placeholder="请输入标签名称"
            maxlength="30"
            show-word-limit
            @keyup.enter="submitNewFolder"
          />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="folderForm.color" :predefine="folderColors" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="folderSaving" @click="submitNewFolder">创建</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  EditPen, Message, Promotion, Document, Delete, Star, Warning,
  Folder, User, Fold, Expand, Search, Plus, Setting, Menu
} from '@element-plus/icons-vue';
import { useAppStore } from '@/stores/app';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';
import { useAiStore } from '@/stores/ai';
import { useNotificationStore } from '@/stores/notification';
import { inboxApi, spamApi, labelApi } from '@/api/mail';
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
const folderDialogVisible = ref(false);
const folderSaving = ref(false);
const folderNameInputRef = ref();
const folderColors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9b59b6'];
const folderForm = reactive({ name: '', color: '#409eff' });

function labelMenuIndex(item) {
  const name = encodeURIComponent(item.name || '');
  return `/mail_list?labelId=${item.id}&labelName=${name}`;
}

const activeMenu = computed(() => {
  if (route.fullPath.includes('/mail_contacts/group_members')) {
    return route.fullPath;
  }
  if (route.path.startsWith('/mail_list') && route.query.labelId) {
    return route.fullPath;
  }
  if (route.path.startsWith('/mail_contacts') && route.fullPath.includes('?')) {
    return route.fullPath;
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

function openNewFolderDialog() {
  resetFolderForm();
  folderDialogVisible.value = true;
  nextTick(() => folderNameInputRef.value?.focus?.());
}

function resetFolderForm() {
  folderForm.name = '';
  folderForm.color = '#409eff';
}

async function submitNewFolder() {
  const name = folderForm.name?.trim();
  if (!name) {
    ElMessage.warning('请先输入标签名称');
    return;
  }
  folderSaving.value = true;
  try {
    await labelApi.add({ name, color: folderForm.color });
    ElMessage.success('标签已创建');
    folderDialogVisible.value = false;
    await menuStore.reloadMenus();
  } finally {
    folderSaving.value = false;
  }
}

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
.layout-root { height: 100vh; flex-direction: column; background: #f6f8fc; }
.layout-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 16px;
  background: #ffffff;
  border-bottom: 1px solid #f1f3f4;
  height: 64px;
  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
    width: 240px;
  }
  .sidebar-toggle-btn {
    color: #5f6368;
    &:hover {
      background-color: rgba(60,64,67,0.08);
    }
  }
}
.header-logo { cursor: pointer; flex-shrink: 0; display: flex; align-items: center; }
.logo-text { font-size: 22px; font-weight: 500; color: #1f1f1f; font-family: 'Google Sans', Roboto, Arial, sans-serif; }

.header-search {
  flex: 1;
  max-width: 720px;
  margin-left: 8px;
  :deep(.el-input__wrapper) {
    background-color: #f1f3f4;
    border-radius: 24px;
    box-shadow: none !important;
    border: 1px solid transparent;
    padding: 0 16px;
    height: 48px;
    transition: background-color 0.2s, box-shadow 0.2s;
    &:hover {
      background-color: #eef1f2;
    }
    &.is-focus {
      background-color: #ffffff;
      box-shadow: 0 1px 1px 0 rgba(65,69,73,0.3), 0 1px 3px 1px rgba(65,69,73,0.15) !important;
    }
  }
  :deep(.el-input__inner) {
    font-size: 15px;
  }
}

.header-right { display: flex; align-items: center; gap: 16px; margin-left: auto; }
.user-meta { display: flex; flex-direction: column; line-height: 1.2; }
.user-name { font-size: 13px; font-weight: 600; color: #3c4043; }
.user-email { font-size: 11px; color: #5f6368; }
.layout-body { flex: 1; overflow: hidden; }

.layout-aside {
  display: flex;
  flex-direction: column;
  background: #f6f8fc;
  border-right: none;
  overflow: hidden;
  transition: width 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.compose-btn {
  margin: 16px 12px;
  height: 48px;
  border-radius: 16px;
  font-weight: 500;
  font-size: 14px;
  background: #c2e7ff !important;
  border: none !important;
  color: #001d35 !important;
  box-shadow: 0 1px 3px 0 rgba(60,64,67,0.2), 0 2px 6px 2px rgba(60,64,67,0.1) !important;
  transition: box-shadow 0.2s, background-color 0.2s;
  &:hover {
    background: #b3d8ef !important;
    box-shadow: 0 1px 3px 0 rgba(60,64,67,0.3), 0 4px 8px 3px rgba(60,64,67,0.15) !important;
  }
  .el-icon {
    font-size: 18px;
    margin-right: 8px;
  }
}

.compose-btn-mini {
  margin: 16px auto;
  display: block;
  width: 48px;
  height: 48px;
  border-radius: 16px;
  background: #c2e7ff !important;
  border: none !important;
  color: #001d35 !important;
  box-shadow: 0 1px 3px 0 rgba(60,64,67,0.2), 0 2px 6px 2px rgba(60,64,67,0.1) !important;
  transition: box-shadow 0.2s, background-color 0.2s;
  &:hover {
    background: #b3d8ef !important;
    box-shadow: 0 1px 3px 0 rgba(60,64,67,0.3), 0 4px 8px 3px rgba(60,64,67,0.15) !important;
  }
  .el-icon {
    font-size: 18px;
  }
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
  background: transparent;
  padding-right: 8px;
  
  :deep(.el-menu-item) {
    height: 40px;
    line-height: 40px;
    margin: 0 0 4px 12px;
    border-radius: 20px;
    color: #444746;
    font-size: 14px;
    padding-left: 16px !important;
    
    .el-icon {
      color: #444746;
      font-size: 18px;
    }
    
    &:hover {
      background-color: rgba(60,64,67,0.04);
      color: #1f1f1f;
    }
    
    &.is-active {
      background-color: #d3e3fd;
      color: #041e49;
      font-weight: 500;
      .el-icon {
        color: #041e49;
      }
    }
  }

  :deep(.el-sub-menu) {
    margin-left: 12px;
    
    .el-sub-menu__title {
      height: 40px;
      line-height: 40px;
      border-radius: 20px;
      color: #444746;
      font-size: 14px;
      padding-left: 16px !important;
      
      .el-icon {
        color: #444746;
        font-size: 18px;
      }
      
      &:hover {
        background-color: rgba(60,64,67,0.04);
        color: #1f1f1f;
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
  font-size: 11px;
  background: #0b57d0;
  color: #fff;
  padding: 0 8px;
  border-radius: 10px;
  line-height: 18px;
  font-weight: 600;
}
.menu-badge.spam { background: #b06000; }
.folder-item-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
}
.folder-add-row { display: flex; align-items: center; gap: 4px; color: #0b57d0; }
.layout-main { padding: 0; overflow: auto; background: #ffffff; border-radius: 16px 16px 0 0; margin-right: 0; box-shadow: inset 0 1px 3px rgba(0,0,0,0.05); }

.layout-ai-aside {
  width: 380px;
  height: 100%;
  background: #ffffff;
  border-left: 1px solid #e0e2e6;
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

