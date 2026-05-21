<template>
  <div class="spam-filter-switch" @click.stop>
    <el-tooltip
      content="开启后，新收到的邮件会自动发送到垃圾邮件检测服务。若被识别为垃圾邮件，将自动移入垃圾邮件箱并记录原因。"
      placement="bottom"
    >
      <el-switch
        v-model="enabled"
        :loading="loading"
        inline-prompt
        active-text="垃圾过滤已开"
        inactive-text="垃圾过滤已关"
        @change="onChange"
      />
    </el-tooltip>
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
        '开启后，新收到的邮件会自动发送到外部垃圾邮件检测服务。如果检测服务不可用，开启会失败。',
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
  margin-left: 8px;
}
</style>
