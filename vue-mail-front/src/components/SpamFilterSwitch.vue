<template>
  <div class="spam-filter-switch" @click.stop>
    <el-tooltip
      content="开启后，新收到的邮件会由系统内置垃圾邮件模型自动检测。若被识别为垃圾邮件，将自动移入垃圾邮件箱并记录原因。"
      placement="bottom"
    >
      <el-switch
        v-model="enabled"
        :loading="loading"
        @change="onChange"
      />
    </el-tooltip>
    <span class="switch-status">{{ enabled ? '已开启' : '已关闭' }}</span>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { spamFilterApi } from '@/api/spamFilter';

const enabled = ref(false);
const loading = ref(false);

onMounted(loadStatus);

async function loadStatus() {
  try {
    const { data } = await spamFilterApi.status();
    enabled.value = !!data?.enabled;
  } catch {
    enabled.value = false;
  }
}

async function onChange(val) {
  if (val) {
    try {
      await ElMessageBox.confirm(
        '开启后，新收到的邮件会自动交给系统内置垃圾邮件模型检测。识别为垃圾邮件时，会自动移入垃圾邮件箱。',
        '开启垃圾邮件过滤',
        { type: 'info', confirmButtonText: '开启', cancelButtonText: '取消' }
      );
    } catch {
      enabled.value = false;
      return;
    }
  }

  loading.value = true;
  try {
    if (val) {
      await spamFilterApi.enable();
      ElMessage.success('已开启垃圾邮件过滤。');
    } else {
      await spamFilterApi.disable();
      ElMessage.success('已关闭垃圾邮件过滤。');
    }
  } catch (error) {
    enabled.value = !val;
    ElMessage.error(error?.response?.data?.message || '更新垃圾邮件过滤失败。');
  } finally {
    loading.value = false;
  }
}

defineExpose({ reload: loadStatus });
</script>

<style scoped>
.spam-filter-switch {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.switch-status {
  color: #6b7280;
  font-size: 13px;
  white-space: nowrap;
}

:deep(.el-switch.is-checked .el-switch__core) {
  background-color: #5b7cfa;
  border-color: #5b7cfa;
}

:deep(.el-switch__core) {
  background-color: #d1d5db;
  border-color: #d1d5db;
}
</style>
