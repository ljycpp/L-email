<template>
  <div class="directory-page">
    <div class="page-header">
      <h2 class="page-title">标签管理</h2>
      <p class="page-desc">创建自定义标签，为邮件分类，并按标签浏览邮件。</p>
    </div>

    <div class="filter-toolbar">
      <el-button class="soft-btn primary" @click="openDialog()">新建标签</el-button>
      <el-button class="soft-btn secondary" @click="load">刷新</el-button>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="list">
        <template #empty>
          <el-empty description="暂无标签，创建后可更清晰地区分邮件。" />
        </template>
        <el-table-column label="标签" min-width="220">
          <template #default="{ row }">
            <button type="button" :class="['label-pill', labelTone(row)]" @click="goMails(row)">
              {{ row.name }}
            </button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button size="small" class="soft-btn light-primary" @click="goMails(row)">查看邮件</el-button>
              <el-button size="small" class="soft-btn secondary" @click="openDialog(row)">编辑</el-button>
              <el-button size="small" class="soft-btn danger" @click="remove(row.id)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑标签' : '新建标签'" width="420px" destroy-on-close>
      <el-form :model="form" label-width="80px" @submit.prevent="submit">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="请输入标签名称" maxlength="30" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="form.color" :predefine="labelColors" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="soft-btn secondary" @click="visible = false">取消</el-button>
        <el-button class="soft-btn primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { labelApi } from '@/api/mail';
import { useMenuStore } from '@/stores/menu';

const router = useRouter();
const menuStore = useMenuStore();
const loading = ref(false);
const list = ref([]);
const visible = ref(false);
const labelColors = ['#5b7cfa', '#ef4444', '#f59e0b', '#22c55e', '#6b7280', '#8b5cf6'];
const form = reactive({ id: null, name: '', color: '#5b7cfa' });

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const { data } = await labelApi.list();
    list.value = data.labelList || [];
  } finally {
    loading.value = false;
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, row);
  } else {
    Object.assign(form, { id: null, name: '', color: '#5b7cfa' });
  }
  visible.value = true;
}

async function submit() {
  const name = form.name?.trim();
  if (!name) {
    ElMessage.warning('请先输入标签名称');
    return;
  }
  const duplicated = list.value.some(item =>
    String(item.id) !== String(form.id || '') &&
    String(item.name || '').trim().toLowerCase() === name.toLowerCase()
  );
  if (duplicated) {
    ElMessage.warning('标签名称已存在');
    return;
  }
  form.name = name;
  if (form.id) {
    await labelApi.edit(form);
  } else {
    await labelApi.add(form);
  }
  ElMessage.success('标签已保存');
  visible.value = false;
  await menuStore.reloadMenus();
  load();
}

async function remove(id) {
  await ElMessageBox.confirm(
    '确定删除该标签吗？已打上此标签的邮件将自动移除该标签关联。',
    '确认',
    { type: 'warning' }
  );
  await labelApi.remove(id);
  ElMessage.success('标签已删除');
  await menuStore.reloadMenus();
  load();
}

function goMails(row) {
  router.push({
    path: '/mail_list',
    query: { labelId: row.id, labelName: row.name }
  });
}

function labelTone(row) {
  const name = String(row.name || '');
  if (name.includes('重要')) return 'danger';
  if (name.includes('待处理') || name.includes('待办')) return 'warning';
  return 'primary';
}
</script>

<style scoped lang="scss">
.directory-page {
  min-height: 100%;
  padding: 24px;
  background: #f6f8fb;
}

.page-header {
  margin-bottom: 18px;
}

.page-title {
  margin: 0 0 6px;
  color: #1f2937;
  font-size: 22px;
  font-weight: 650;
}

.page-desc {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.filter-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.table-card {
  overflow: hidden;
  background: #fff;
  border: 1px solid #e7ebf3;
  border-radius: 16px;
}

.table-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.label-pill {
  height: 28px;
  padding: 0 12px;
  border: 0;
  border-radius: 999px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}

.label-pill.primary {
  color: #5b7cfa;
  background: #e8edff;
}

.label-pill.danger {
  color: #ef4444;
  background: #fee2e2;
}

.label-pill.warning {
  color: #b86b00;
  background: #fff4e5;
}

.soft-btn {
  height: 36px;
  padding: 0 16px;
  border-radius: 10px;
  font-weight: 500;
}

.soft-btn.primary {
  color: #fff;
  background: #5b7cfa;
  border-color: #5b7cfa;
}

.soft-btn.primary:hover {
  background: #4667e8;
  border-color: #4667e8;
}

.soft-btn.secondary {
  color: #4b5563;
  background: #fff;
  border-color: #dde3ee;
}

.soft-btn.secondary:hover {
  background: #f2f5fb;
  border-color: #d7dfed;
}

.soft-btn.light-primary {
  color: #5b7cfa;
  background: #e8edff;
  border-color: #e8edff;
}

.soft-btn.danger {
  color: #ef4444;
  background: #fee2e2;
  border-color: #fee2e2;
}

.soft-btn.danger:hover {
  background: #fecaca;
  border-color: #fecaca;
}

:deep(.el-table) {
  color: #1f2937;
  --el-table-border-color: #e7ebf3;
  --el-table-header-bg-color: #f9fafc;
  --el-table-row-hover-bg-color: #f8faff;
}

:deep(.el-table__header th) {
  height: 48px;
  color: #6b7280;
  background: #f9fafc;
  font-weight: 600;
}

:deep(.el-table__body td) {
  height: 56px;
  border-bottom-color: #eef2f7;
}

:deep(.el-table::before),
:deep(.el-table__inner-wrapper::before) {
  display: none;
}

:deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #dde3ee inset;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #5b7cfa inset;
}
</style>
