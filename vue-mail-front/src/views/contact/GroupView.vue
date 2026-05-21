<template>
  <div class="directory-page">
    <div class="page-header">
      <h2 class="page-title">联系人分组</h2>
      <p class="page-desc">将联系人归类到分组，可一键向整组发信。双击分组可进入成员页。</p>
    </div>

    <div class="filter-toolbar">
      <el-button type="primary" @click="openDialog()">新建分组</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border @row-dblclick="viewMembers">
      <template #empty>
        <el-empty description="暂无分组，新建后可更好地管理联系人。" />
      </template>
      <el-table-column prop="name" label="分组名称" min-width="180" />
      <el-table-column label="成员数" width="100" align="center">
        <template #default="{ row }">{{ row.contacts?.length || 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="sendToGroup(row)">组内发信</el-button>
          <el-button size="small" @click="viewMembers(row)">查看成员</el-button>
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑分组' : '新建分组'" width="400px" destroy-on-close>
      <el-form label-width="80px" @submit.prevent="submit">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="请输入分组名称" maxlength="30" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { groupApi } from '@/api/mail';
import { useMenuStore } from '@/stores/menu';
import { useMailStore } from '@/stores/mail';

const route = useRoute();
const router = useRouter();
const mailStore = useMailStore();
const menuStore = useMenuStore();
const loading = ref(false);
const list = ref([]);
const visible = ref(false);
const form = reactive({ id: null, name: '' });

watch(
  () => route.query.create,
  value => {
    if (value === '1') {
      openDialog();
      router.replace('/mail_contacts/group');
    }
  },
  { immediate: true }
);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const { data } = await groupApi.list();
    list.value = data.groupList || [];
  } finally {
    loading.value = false;
  }
}

function openDialog(row) {
  Object.assign(form, row ? { id: row.id, name: row.name } : { id: null, name: '' });
  visible.value = true;
}

async function submit() {
  const name = form.name?.trim();
  if (!name) {
    ElMessage.warning('请先输入分组名称');
    return;
  }
  if (form.id) await groupApi.edit({ id: form.id, name });
  else await groupApi.add({ name });
  ElMessage.success('分组已保存');
  visible.value = false;
  await menuStore.reloadMenus();
  load();
}

async function remove(id) {
  await ElMessageBox.confirm('确定删除该分组吗？组内联系人将变为未分组状态。', '确认', { type: 'warning' });
  await groupApi.remove(id);
  ElMessage.success('分组已删除');
  await menuStore.reloadMenus();
  load();
}

function viewMembers(row) {
  router.push({ path: '/mail_contacts/group_members', query: { groupId: row.id } });
}

async function sendToGroup(row) {
  const { data } = await groupApi.contacts(row.id);
  const contacts = data.contacts || [];
  if (!contacts.length) {
    ElMessage.warning('该分组暂无联系人');
    return;
  }
  mailStore.setTarget(contacts.map(contact => ({ ...contact, show: `${contact.name}<${contact.mail}>` })));
  mailStore.setPageType('add');
  router.push('/mail_send');
}
</script>

<style scoped lang="scss">
.directory-page { padding: 16px 20px; }
.page-header { margin-bottom: 16px; }
.page-title { margin: 0 0 4px; font-size: 20px; font-weight: 600; }
.page-desc { margin: 0; color: #909399; font-size: 13px; }
.filter-toolbar { display: flex; gap: 8px; margin-bottom: 16px; }
</style>
