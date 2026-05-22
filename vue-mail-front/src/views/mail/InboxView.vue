<template>
  <QqMailList
    folder-title="收件箱"
    cache-key="inbox"
    empty-text="收件箱为空"
    :fetch-list="inboxApi.list"
    :delete-mail="inboxApi.delete"
    :list-params="{ spam: 0 }"
    mail-type="receive"
    show-sender-filter
    show-label-filter
    show-read-filter
    show-mark-all-read
    show-report
    show-move-to
    :on-mark-all-read="markAllRead"
    :on-report-spam="reportSpam"
    @loaded="$emit('stats-change')"
  />
</template>

<script setup>
import QqMailList from '@/components/QqMailList.vue';
import { inboxApi, mailActionApi } from '@/api/mail';

defineEmits(['stats-change']);

async function markAllRead() {
  await inboxApi.markAllRead();
}

async function reportSpam(ids) {
  await mailActionApi.reportSpam(ids);
}
</script>
