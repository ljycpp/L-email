<template>
  <QqMailList
    folder-title="草稿箱"
    cache-key="draft"
    empty-text="暂无草稿"
    :fetch-list="draftApi.list"
    :delete-mail="draftApi.delete"
    mail-type="draft"
    open-mode="draft"
    date-field="lastModifyDate"
    row-display="recipient"
    show-recipient-filter
    show-label-filter
    :show-sender-filter="false"
    :show-read-filter="false"
    :show-forward="false"
    :show-star="false"
    delete-confirm-text="确定删除选中的 {count} 封草稿吗？"
  />
</template>

<script setup>
import QqMailList from '@/components/QqMailList.vue';
import { draftApi } from '@/api/mail';
</script>
