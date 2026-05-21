<template>
  <QqMailList
    folder-title="垃圾邮件"
    cache-key="spam"
    empty-text="暂无垃圾邮件"
    :fetch-list="spamApi.list"
    :delete-mail="inboxApi.delete"
    :list-params="{ spam: 1 }"
    mail-type="receive"
    :show-forward="false"
    :show-mark-all-read="false"
    :show-report="false"
    :show-move-to="false"
    :show-read-filter="true"
    show-sender-filter
    show-label-filter
    show-not-spam
    :on-not-spam="notSpam"
    @loaded="onLoaded"
  />
</template>

<script setup>
import QqMailList from '@/components/QqMailList.vue';
import { inboxApi, spamApi, mailActionApi } from '@/api/mail';

const emit = defineEmits(['stats-change']);

function onLoaded() {
  emit('stats-change');
}

async function notSpam(ids) {
  await mailActionApi.notSpam(ids);
}
</script>
