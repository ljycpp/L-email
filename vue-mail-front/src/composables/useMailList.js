import { reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { labelApi } from '@/api/mail';
import {
  getMailListCache,
  getMailListQueryCache,
  setMailListCache,
  setMailListQueryCache
} from '@/utils/mailCache';

export function useMailList(fetchListApi, deleteApi, options = {}) {
  const list = ref([]);
  const total = ref(0);
  const loading = ref(false);
  const labelList = ref([]);
  const multipleSelection = ref([]);
  const listQuery = reactive({
    page: 1,
    limit: 20,
    title: undefined,
    receiveMail: undefined,
    receiveName: undefined,
    status: undefined,
    labelId: undefined,
    sort: '',
    order: ''
  });

  async function loadList(extra = {}) {
    loading.value = true;
    try {
      const { data } = await fetchListApi({ ...listQuery, ...extra });
      list.value = data.items || [];
      total.value = data.total || 0;
      setMailListCache(options.cacheKey, {
        items: list.value,
        total: total.value
      });
      setMailListQueryCache(options.cacheKey, {
        title: listQuery.title,
        receiveMail: listQuery.receiveMail,
        receiveName: listQuery.receiveName,
        status: listQuery.status,
        labelId: listQuery.labelId
      });
    } catch (error) {
      const cached = getMailListCache(options.cacheKey);
      if (!cached) {
        throw error;
      }
      list.value = cached.items || [];
      total.value = cached.total || 0;
      ElMessage.warning('网络请求失败，已显示本地缓存的邮件数据。');
    } finally {
      loading.value = false;
    }
  }

  function hydrateCache() {
    const cached = getMailListCache(options.cacheKey);
    if (!cached) {
      return false;
    }
    list.value = cached.items || [];
    total.value = cached.total || 0;
    return true;
  }

  function hydrateQueryCache() {
    const cached = getMailListQueryCache(options.cacheKey);
    if (!cached) {
      return false;
    }
    listQuery.title = cached.title;
    listQuery.receiveMail = cached.receiveMail;
    listQuery.receiveName = cached.receiveName;
    listQuery.status = cached.status;
    listQuery.labelId = cached.labelId;
    return true;
  }

  async function loadLabels() {
    const { data } = await labelApi.list();
    labelList.value = data.labelList || [];
  }

  function handleSelectionChange(rows) {
    multipleSelection.value = rows;
  }

  function getSelectedIds() {
    return multipleSelection.value.map(item => item.id);
  }

  async function handleDelete(confirmText = '将选中的 {count} 封邮件移入回收站？') {
    if (!deleteApi) {
      return;
    }
    const count = multipleSelection.value.length;
    if (!count) {
      ElMessage.warning('请先选择至少一封邮件。');
      return;
    }
    await ElMessageBox.confirm(confirmText.replace('{count}', count), '提示', { type: 'warning' });
    await deleteApi(getSelectedIds());
    ElMessage.success('已移入回收站。');
    await loadList();
  }

  async function handleRestore() {
    const restoreApi = options.restoreApi;
    if (!restoreApi) {
      return;
    }
    const count = multipleSelection.value.length;
    if (!count) {
      ElMessage.warning('请先选择至少一封邮件。');
      return;
    }
    await ElMessageBox.confirm(`确认恢复选中的 ${count} 封邮件吗？`, '提示', { type: 'warning' });
    await restoreApi(getSelectedIds());
    ElMessage.success('邮件已恢复。');
    await loadList();
  }

  async function handlePermanentDelete() {
    const permanentApi = options.permanentDeleteApi;
    if (!permanentApi) {
      return;
    }
    const count = multipleSelection.value.length;
    if (!count) {
      ElMessage.warning('请先选择至少一封邮件。');
      return;
    }
    await ElMessageBox.confirm(
      `确认彻底删除选中的 ${count} 封邮件吗？此操作无法撤销。`,
      '警告',
      { type: 'warning' }
    );
    await permanentApi(getSelectedIds());
    ElMessage.success('邮件已彻底删除。');
    await loadList();
  }

  async function toggleStar(row) {
    const ids = row ? [row.id] : getSelectedIds();
    if (!ids.length) {
      ElMessage.warning('请先选择至少一封邮件。');
      return;
    }
    await labelApi.toggleStar(ids);
    if (row) {
      row.isStar = !row.isStar;
    }
  }

  async function handleMark(labelId) {
    if (labelId === 'star') {
      await toggleStar();
      ElMessage.success('星标状态已更新。');
      await loadList();
      return;
    }
    const ids = getSelectedIds();
    if (!ids.length) {
      ElMessage.warning('请先选择至少一封邮件。');
      return;
    }
    await labelApi.mark(labelId, ids);
    ElMessage.success('标签已更新。');
    await loadList();
  }

  return {
    list,
    total,
    loading,
    labelList,
    multipleSelection,
    listQuery,
    hydrateCache,
    hydrateQueryCache,
    loadList,
    loadLabels,
    handleSelectionChange,
    handleDelete,
    handleRestore,
    handlePermanentDelete,
    toggleStar,
    handleMark
  };
}
