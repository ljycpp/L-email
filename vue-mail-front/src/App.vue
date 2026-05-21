<template>
  <router-view />
  <NewMailToastHost />
</template>

<script setup>
import { onMounted } from 'vue';
import NewMailToastHost from '@/components/NewMailToastHost.vue';
import { useMailWebSocket } from '@/composables/useMailWebSocket';

const { requestNotificationPermission } = useMailWebSocket();

onMounted(() => {
  requestNotificationPermission();
});
</script>
