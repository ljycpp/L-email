<template>
  <LabelMailView v-if="route.query.labelId" />
  <TrashView v-else />
</template>

<script setup>
import { useRoute } from 'vue-router';
import TrashView from './TrashView.vue';
import LabelMailView from './LabelMailView.vue';

const route = useRoute();
</script>
