<template>
  <QqMailList
    folder-title="回收站"
    cache-key="trash"
    empty-text="回收站为空"
    :fetch-list="mailListApi.list"
    :list-params="trashParams"
    is-trash
    :restore-mail="mailListApi.restore"
    :delete-permanently-mail="mailListApi.deletePermanently"
    mail-type="receive"
    :show-forward="false"
    :show-mark-all-read="false"
    :show-report="false"
    :show-move-to="false"
    :show-mark="false"
    :show-sender-filter="true"
    show-label-filter
    :show-read-filter="true"
    date-field="date"
  />
</template>

<script setup>
import { computed } from 'vue';
import QqMailList from '@/components/QqMailList.vue';
import { mailListApi } from '@/api/mail';

const trashParams = computed(() => ({
  'routeQuery[isDeleted]': 'true'
}));
</script>
