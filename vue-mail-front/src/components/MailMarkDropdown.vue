<template>
  <el-dropdown split-button type="primary" @command="emit('mark', $event)">
    添加标签
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="star">设为星标邮件</el-dropdown-item>
        <el-dropdown-item v-for="label in labels" :key="label.id" :command="String(label.id)">
          {{ label.name }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
defineProps({
  labels: { type: Array, default: () => [] }
});

const emit = defineEmits(['mark']);
</script>
