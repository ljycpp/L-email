<template>
  <div v-if="files.length" class="mail-attachments">
    <span class="mail-attachments-title">{{ title }}</span>
    <div class="mail-attachments-list">
      <a
        v-for="file in files"
        :key="file.id || file.url"
        href="#"
        class="mail-attachment-item"
        @click.prevent="handleDownload(file)"
      >
        <el-icon><Document /></el-icon>
        <span class="name">{{ file.name }}</span>
        <span v-if="file.size" class="size">{{ formatFileSize(file.size) }}</span>
      </a>
    </div>
  </div>
</template>

<script setup>
import { Document } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { formatFileSize } from '@/utils/format';
import { downloadAttachment } from '@/utils/attachment';

defineProps({
  files: { type: Array, default: () => [] },
  title: { type: String, default: '附件' }
});

async function handleDownload(file) {
  try {
    await downloadAttachment(file);
  } catch {
    ElMessage.error('附件下载失败，请稍后重试');
  }
}
</script>

<style scoped lang="scss">
.mail-attachments {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 8px 12px;
  margin-top: 12px;
}
.mail-attachments-title {
  font-weight: 600;
  color: #606266;
  line-height: 32px;
}
.mail-attachments-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.mail-attachment-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #f5f7fa;
  color: #303133;
  text-decoration: none;
  font-size: 13px;
  transition: border-color 0.2s, background 0.2s;
  cursor: pointer;

  &:hover {
    border-color: #409eff;
    background: #ecf5ff;
    color: #409eff;
  }
}
.name {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.size {
  color: #909399;
  font-size: 12px;
}
</style>
