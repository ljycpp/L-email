<template>
  <QqMailList
    folder-title="发件箱"
    cache-key="outbox"
    empty-text="暂无已发送邮件"
    :fetch-list="outboxApi.list"
    :delete-mail="outboxApi.delete"
    mail-type="send"
    date-field="sendDate"
    row-display="recipient"
    show-recipient-filter
    show-label-filter
    :show-sender-filter="false"
    :show-read-filter="false"
    show-forward
    show-edit
    :show-mark-all-read="false"
    :show-report="false"
  />
</template>

<script setup>
import QqMailList from '@/components/QqMailList.vue';
import { outboxApi } from '@/api/mail';
</script>
