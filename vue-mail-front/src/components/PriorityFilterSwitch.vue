<template>
  <div class="priority-filter-switch" @click.stop>
    <el-tooltip
      content="开启后，新收到的邮件会由 AI 自动评分，并按优先级排序。需要先在 AI 设置中完成可用的第三方模型接入配置。"
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
import { mailPriorityApi } from '@/api/mailPriority';

const enabled = ref(false);
const loading = ref(false);
const aiConfigured = ref(false);

onMounted(loadStatus);

async function loadStatus() {
  try {
    const { data } = await mailPriorityApi.status();
    enabled.value = !!data?.enabled;
    aiConfigured.value = !!data?.aiConfigured;
  } catch {
    enabled.value = false;
    aiConfigured.value = false;
  }
}

async function onChange(val) {
  if (val) {
    try {
      await ElMessageBox.confirm(
        aiConfigured.value
          ? '开启后，新收到的邮件会自动评分，高优先级邮件会置顶显示。'
          : 'AI 设置尚未完成，请先配置并启用可用的第三方模型接入信息后再开启自动优先级判断。',
        '开启自动优先级判断',
        {
          type: aiConfigured.value ? 'info' : 'warning',
          confirmButtonText: aiConfigured.value ? '开启' : '确定',
          cancelButtonText: '取消',
          showCancelButton: true
        }
      );
      if (!aiConfigured.value) {
        enabled.value = false;
        return;
      }
    } catch {
      enabled.value = false;
      return;
    }
  }

  loading.value = true;
  try {
    if (val) {
      await mailPriorityApi.enable();
      ElMessage.success('已开启自动优先级判断。');
    } else {
      await mailPriorityApi.disable();
      ElMessage.success('已关闭自动优先级判断。');
    }
  } catch (error) {
    enabled.value = !val;
    ElMessage.error(error?.response?.data?.message || '更新自动优先级判断失败。');
    await loadStatus();
  } finally {
    loading.value = false;
  }
}

defineExpose({ reload: loadStatus });
</script>

<style scoped>
.priority-filter-switch {
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
