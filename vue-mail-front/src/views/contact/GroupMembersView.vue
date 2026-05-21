<template>
  <div class="directory-page" v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ groupName || '分组成员' }}</h2>
        <p class="page-desc">查看当前分组中的成员，并支持组内发信或删除分组。</p>
      </div>
      <div class="header-actions">
        <el-button @click="goBack">返回分组管理</el-button>
        <el-button type="primary" @click="sendToGroup" :disabled="!contacts.length">组内发信</el-button>
        <el-button type="danger" @click="removeGroup">删除分组</el-button>
      </div>
    </div>

    <el-empty v-if="!contacts.length && !loading" description="该分组暂无成员。" />

    <el-table v-else :data="contacts" border>
      <el-table-column label="姓名" min-width="180">
        <template #default="{ row }">
          <div class="member-cell">
            <el-avatar :src="row.avatarUrl" :size="40">{{ row.name?.charAt(0) }}</el-avatar>
            <div class="member-meta">
              <div class="member-name">{{ row.name }}</div>
              <div class="member-mail">{{ row.mail }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="mail" label="邮箱" min-width="220" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="writeTo(row)">写信</el-button>
          <el-button size="small" @click="editContact(row)">编辑联系人</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { contactApi, groupApi } from '@/api/mail';
import { resolveApiUrl } from '@/utils/url';
import { useMailStore } from '@/stores/mail';
import { useMenuStore } from '@/stores/menu';

const route = useRoute();
const router = useRouter();
const mailStore = useMailStore();
const menuStore = useMenuStore();

const loading = ref(false);
const contacts = ref([]);
const groups = ref([]);

const groupId = computed(() => Number(route.query.groupId || 0));
const groupName = computed(() => groups.value.find(item => Number(item.id) === groupId.value)?.name || '');

watch(groupId, () => {
  load();
}, { immediate: true });

onMounted(async () => {
  const { data } = await groupApi.list();
  groups.value = data.groupList || [];
});

async function load() {
  if (!groupId.value) {
    contacts.value = [];
    return;
  }
  loading.value = true;
  try {
    const { data } = await groupApi.contacts(groupId.value);
    contacts.value = (data.contacts || []).map(item => ({ ...item, avatarUrl: resolveApiUrl(item.avatarUrl) }));
  } finally {
    loading.value = false;
  }
}

function goBack() {
  router.push('/mail_contacts/group');
}

function writeTo(contact) {
  mailStore.setTarget([{ ...contact, show: `${contact.name}<${contact.mail}>` }]);
  mailStore.setPageType('add');
  router.push('/mail_send');
}

function editContact(contact) {
  router.push({ path: '/mail_contacts', query: { groupId: groupId.value } });
  ElMessage.info('你可以在联系人页继续编辑该成员。');
}

async function sendToGroup() {
  if (!contacts.value.length) {
    ElMessage.warning('该分组暂无成员');
    return;
  }
  mailStore.setTarget(contacts.value.map(contact => ({ ...contact, show: `${contact.name}<${contact.mail}>` })));
  mailStore.setPageType('add');
  router.push('/mail_send');
}

async function removeGroup() {
  await ElMessageBox.confirm('确定删除该分组吗？组内联系人将变为未分组状态。', '确认', { type: 'warning' });
  await groupApi.remove(groupId.value);
  ElMessage.success('分组已删除');
  await menuStore.reloadMenus();
  router.push('/mail_contacts/group');
}
</script>

<style scoped lang="scss">
.directory-page { padding: 16px 20px; }
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.page-title { margin: 0 0 4px; font-size: 22px; font-weight: 600; }
.page-desc { margin: 0; color: #909399; font-size: 13px; }
.header-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.member-cell { display: flex; align-items: center; gap: 12px; }
.member-name { font-weight: 600; }
.member-mail { color: #909399; font-size: 13px; }
</style>
