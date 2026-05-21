<template>
  <QqMailList
    :folder-title="folderTitle"
    :cache-key="cacheKey"
    empty-text="该标签下暂无邮件"
    :fetch-list="mailListApi.list"
    :delete-mail="inboxApi.delete"
    :list-params="labelParams"
    mail-type="receive"
    :show-label-filter="false"
    :show-sender-filter="true"
    :show-read-filter="true"
    show-move-to
    :show-report="true"
    :on-report-spam="reportSpam"
  />
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import QqMailList from '@/components/QqMailList.vue';
import { inboxApi, mailListApi, mailActionApi } from '@/api/mail';

async function reportSpam(ids) {
  await mailActionApi.reportSpam(ids);
}

const route = useRoute();

const labelParams = computed(() => {
  const id = route.query.labelId;
  return {
    labelId: id,
    'routeQuery[labelId]': id
  };
});

const cacheKey = computed(() => `label-${route.query.labelId || 'all'}`);

const folderTitle = computed(() => {
  const raw = route.query.labelName;
  if (!raw) {
    return '标签邮件';
  }
  try {
    return `标签：${decodeURIComponent(String(raw))}`;
  } catch {
    return `标签：${raw}`;
  }
});
</script>
