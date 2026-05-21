<template>
  <QqMailList
    folder-title="星标邮件"
    cache-key="starred"
    empty-text="暂无星标邮件"
    :fetch-list="inboxApi.list"
    :delete-mail="inboxApi.delete"
    :list-params="{ starred: 1, spam: 0 }"
    mail-type="receive"
    :show-mark-all-read="false"
    :show-report="true"
    show-move-to
    show-sender-filter
    show-label-filter
    show-read-filter
    :on-report-spam="reportSpam"
  />
</template>

<script setup>
import QqMailList from '@/components/QqMailList.vue';
import { inboxApi, mailActionApi } from '@/api/mail';

async function reportSpam(ids) {
  await mailActionApi.reportSpam(ids);
}
</script>
