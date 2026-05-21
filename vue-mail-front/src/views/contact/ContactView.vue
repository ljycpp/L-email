<template>
  <div class="directory-page">
    <div class="page-header">
      <h2 class="page-title">联系人</h2>
      <p class="page-desc">管理联系人、按分组归类，并快速发起写信。</p>
    </div>

    <div class="filter-toolbar">
      <el-button type="primary" @click="openDialog()">新建联系人</el-button>
      <el-button type="danger" :disabled="!selectedIds.length" @click="batchDelete">删除选中</el-button>
      <el-select v-model="query.groupId" placeholder="全部分组" clearable style="width: 160px" @change="search">
        <el-option v-for="group in groupOptions" :key="group.id" :label="group.name" :value="String(group.id)" />
      </el-select>
      <el-input v-model="query.name" placeholder="按姓名搜索" clearable style="width: 180px" @keyup.enter="search" />
      <el-input v-model="query.mail" placeholder="按邮箱搜索" clearable style="width: 220px" @keyup.enter="search" />
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-empty v-if="!list.length && !loading" description="暂无联系人，新建后可更快写信。" />

    <el-row v-else :gutter="16" v-loading="loading">
      <el-col v-for="item in list" :key="item.id" :span="8" class="mb-16">
        <el-card
          shadow="hover"
          :class="{ selected: selectedIds.includes(item.id) }"
          @click="toggle(item)"
        >
          <div v-if="selectedIds.includes(item.id)" class="selected-badge" aria-label="已选中">
            <span class="selected-badge-circle">✓</span>
          </div>
          <div class="contact-card">
            <el-avatar :src="item.avatarUrl" :size="64" />
            <div class="contact-meta">
              <div class="name">{{ item.name }}</div>
              <div class="mail">{{ item.mail }}</div>
              <div v-if="groupName(item.groupId)" class="group-tag">{{ groupName(item.groupId) }}</div>
            </div>
            <div class="actions" @click.stop>
              <el-button size="small" @click="openDialog(item)">编辑</el-button>
              <el-button size="small" type="primary" @click="sendTo(item)">写信</el-button>
              <el-button size="small" type="danger" @click="removeContact(item)">删除</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div v-if="total > 0" class="pagination-container">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.limit"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑联系人' : '新建联系人'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="头像">
          <el-upload class="avatar-uploader" :show-file-list="false" :http-request="uploadAvatar">
            <el-avatar v-if="form.avatarUrl" :src="form.avatarUrl" :size="80" />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入联系人姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="mail">
          <el-input v-model="form.mail" placeholder="example@lmailbox.com" />
        </el-form-item>
        <el-form-item label="分组">
          <el-select v-model="form.groupId" placeholder="不分组" clearable style="width: 100%">
            <el-option v-for="group in groupOptions" :key="group.id" :label="group.name" :value="group.id" />
          </el-select>
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
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import request from '@/utils/request';
import { contactApi, groupApi } from '@/api/mail';
import { useMailStore } from '@/stores/mail';
import { resolveApiUrl } from '@/utils/url';

const route = useRoute();
const router = useRouter();
const mailStore = useMailStore();

const loading = ref(false);
const list = ref([]);
const total = ref(0);
const selectedIds = ref([]);
const visible = ref(false);
const formRef = ref();
const query = reactive({ page: 1, limit: 12, name: '', mail: '', groupId: route.query.groupId || '' });
const groupOptions = ref([]);
const form = reactive({ id: null, name: '', mail: '', avatarUrl: '', groupId: null });
const rules = {
  name: [{ required: true, message: '请输入联系人姓名', trigger: 'blur' }],
  mail: [{ required: true, type: 'email', message: '请输入有效邮箱地址', trigger: 'blur' }]
};

const groupMap = computed(() => {
  const map = new Map();
  for (const group of groupOptions.value) map.set(group.id, group.name);
  return map;
});

function groupName(groupId) {
  return groupId ? groupMap.value.get(groupId) : '';
}

watch(
  () => route.query.groupId,
  id => {
    query.groupId = id ? String(id) : '';
    query.page = 1;
    load();
  }
);

watch(
  () => route.query.create,
  value => {
    if (value === '1') {
      openDialog();
      router.replace({ path: '/mail_contacts', query: route.query.groupId ? { groupId: route.query.groupId } : {} });
    }
  },
  { immediate: true }
);

onMounted(async () => {
  await loadGroups();
  load();
});

async function loadGroups() {
  const { data } = await groupApi.list();
  groupOptions.value = data.groupList || [];
}

function buildQueryParams() {
  const params = {
    page: query.page,
    limit: query.limit,
    name: query.name?.trim() || undefined,
    mail: query.mail?.trim() || undefined
  };
  if (query.groupId) params.groupId = Number(query.groupId);
  return params;
}

async function load() {
  loading.value = true;
  try {
    const { data } = await contactApi.list(buildQueryParams());
    list.value = (data.contacts || []).map(contact => ({ ...contact, avatarUrl: resolveApiUrl(contact.avatarUrl) }));
    total.value = data.total || 0;
  } finally {
    loading.value = false;
  }
}

function search() {
  query.page = 1;
  load();
}

function resetFilters() {
  query.name = '';
  query.mail = '';
  if (!route.query.groupId) query.groupId = '';
  search();
}

function toggle(item) {
  const index = selectedIds.value.indexOf(item.id);
  if (index >= 0) selectedIds.value.splice(index, 1);
  else selectedIds.value.push(item.id);
}

function openDialog(row) {
  if (row) {
    Object.assign(form, { ...row, groupId: row.groupId ?? null });
  } else {
    const groupId = query.groupId ? Number(query.groupId) : null;
    Object.assign(form, { id: null, name: '', mail: '', avatarUrl: '', groupId });
  }
  visible.value = true;
}

async function uploadAvatar({ file, onSuccess }) {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await request.post('/api/attachments/upload', formData);
  form.avatarUrl = resolveApiUrl(data.url);
  onSuccess(data);
}

async function submit() {
  await formRef.value.validate();
  if (form.id) await contactApi.edit(form);
  else await contactApi.add(form);
  ElMessage.success('联系人已保存');
  visible.value = false;
  load();
}

async function removeContact(item) {
  await ElMessageBox.confirm(`确定删除联系人「${item.name}」吗？`, '确认', { type: 'warning' });
  await contactApi.remove([item.id]);
  selectedIds.value = selectedIds.value.filter(id => id !== item.id);
  ElMessage.success('联系人已删除');
  load();
}

async function batchDelete() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先选择联系人');
    return;
  }
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个联系人吗？`, '确认', { type: 'warning' });
  await contactApi.remove(selectedIds.value);
  selectedIds.value = [];
  ElMessage.success('联系人已删除');
  load();
}

function sendTo(item) {
  mailStore.setTarget([{ ...item, show: `${item.name}<${item.mail}>` }]);
  mailStore.setPageType('add');
  router.push('/mail_send');
}
</script>

<style scoped lang="scss">
.directory-page { padding: 16px 20px; }
.page-header { margin-bottom: 16px; }
.page-title { margin: 0 0 4px; font-size: 20px; font-weight: 600; }
.page-desc { margin: 0; color: #909399; font-size: 13px; }
.filter-toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 16px; }
.mb-16 { margin-bottom: 16px; }
.contact-card { display: flex; gap: 12px; align-items: center; }
.contact-meta { flex: 1; min-width: 0; }
.name { font-weight: 600; }
.mail { color: #909399; font-size: 13px; }
.group-tag { font-size: 12px; color: #409eff; margin-top: 4px; }
.actions { flex-shrink: 0; display: flex; flex-direction: column; gap: 6px; }
.selected {
  position: relative;
  border-color: #409eff;
  background: linear-gradient(180deg, #f2f8ff 0%, #ffffff 100%);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.18);
}
.selected-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 2;
}

.selected-badge-circle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 0 2px #fff;
}
.pagination-container { margin-top: 16px; display: flex; justify-content: flex-end; }
.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 80px;
  height: 80px;
  border: 1px dashed #d9d9d9;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
