<template>
  <el-dropdown trigger="click" @command="emit('mark', $event)">
    <el-button text :size="size" class="toolbar-btn">
      <el-icon class="el-icon--left"><CollectionTag /></el-icon>
      标签
      <el-icon class="el-icon--right"><ArrowDown /></el-icon>
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="star">
          <el-icon><Star /></el-icon>设为星标邮件
        </el-dropdown-item>
        <el-dropdown-item v-for="label in labels" :key="label.id" :command="String(label.id)">
          <span class="label-dot" :style="{ background: label.color }" />
          {{ label.name }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { CollectionTag, ArrowDown, Star } from '@element-plus/icons-vue';

defineProps({
  labels: { type: Array, default: () => [] },
  size: { type: String, default: 'small' }
});

const emit = defineEmits(['mark']);
</script>

<style scoped lang="scss">
.toolbar-btn {
  color: var(--color-text-secondary);
  font-size: 13px;
  height: 32px;
  padding: 0 12px;
  border-radius: 10px;
  font-weight: 500;
  border: none;
  
  &:hover {
    background-color: var(--color-hover);
    color: var(--color-primary);
  }
}
.label-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
}
</style>
