<template>
  <div class="directory-page">
    <div class="page-header">
      <h2 class="page-title">标签管理</h2>
      <p class="page-desc">创建自定义标签，为邮件分类，并按标签浏览邮件。</p>
    </div>

    <div class="filter-toolbar">
      <el-button type="primary" @click="openDialog()">新建标签</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <template #empty>
        <el-empty description="暂无标签，创建后可更清晰地区分邮件。" />
      </template>
      <el-table-column label="标签" min-width="220">
        <template #default="{ row }">
          <el-tag :color="row.color" effect="dark" class="label-tag" @click="goMails(row)">
            {{ row.name }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="goMails(row)">查看邮件</el-button>
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

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
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
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
const labelColors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9b59b6'];
const form = reactive({ id: null, name: '', color: '#409eff' });

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
    Object.assign(form, { id: null, name: '', color: '#409eff' });
  }
  visible.value = true;
}

async function submit() {
  const name = form.name?.trim();
  if (!name) {
    ElMessage.warning('请先输入标签名称');
    return;
  }
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
</script>

<style scoped lang="scss">
.directory-page { padding: 16px 20px; }
.page-header { margin-bottom: 16px; }
.page-title { margin: 0 0 4px; font-size: 20px; font-weight: 600; }
.page-desc { margin: 0; color: #909399; font-size: 13px; }
.filter-toolbar { display: flex; gap: 8px; margin-bottom: 16px; }
.label-tag { cursor: pointer; }
</style>
