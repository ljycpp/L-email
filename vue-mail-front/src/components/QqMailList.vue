<template>
  <div v-loading="loading" class="qq-inbox" @click="hideContextMenu">
    <div class="qq-toolbar">
      <div class="toolbar-left">
        <el-checkbox v-model="selectAll" :indeterminate="indeterminate" @change="onSelectAll" />
        <span class="folder-title">{{ folderTitle }}</span>
        <SpamFilterSwitch v-if="showSpamFilterSwitch" />
        <PriorityFilterSwitch v-if="showPriorityFilterSwitch" />
        <el-button text size="small" @click="refresh">刷新</el-button>
        <el-button v-if="isTrash && restoreMail" text size="small" type="success" @click="handleRestore()">恢复</el-button>
        <el-button v-if="isTrash && deletePermanentlyMail" text size="small" type="danger" @click="handlePermanentDelete()">彻底删除</el-button>
        <el-button v-if="!isTrash && deleteMail" text size="small" type="danger" @click="doDelete()">删除</el-button>
        <el-button v-if="showEdit" text size="small" type="primary" @click="goCompose('edit')">编辑</el-button>
        <el-button v-if="showForward" text size="small" @click="goCompose('forward')">转发</el-button>
        <el-button v-if="showMarkAllRead" text size="small" @click="markAllRead">全部标为已读</el-button>
        <el-button v-if="showReport" text size="small" @click="reportSpam">标记为垃圾邮件</el-button>
        <el-button v-if="showNotSpam" text size="small" type="success" @click="markNotSpam">恢复到收件箱</el-button>
        <el-dropdown v-if="showMoveTo && labelList.length" trigger="click" @command="moveToLabel">
          <el-button text size="small">
            添加标签
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="label in labelList" :key="label.id" :command="label.id">
                {{ label.name }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <MailMarkDropdown v-if="showMark" :labels="labelList" @mark="handleMark" />
        <el-input
          v-model="listQuery.title"
          class="search-input"
          placeholder="搜索主题或正文"
          clearable
          :prefix-icon="Search"
          @keyup.enter="search"
          @clear="search"
        />
        <el-input
          v-if="showSenderFilter"
          v-model="listQuery.receiveName"
          class="search-input sender-input"
          placeholder="发件人姓名"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-input
          v-if="showSenderFilter"
          v-model="listQuery.receiveMail"
          class="search-input sender-input"
          placeholder="发件人邮箱"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-input
          v-if="showRecipientFilter"
          v-model="listQuery.receiveName"
          class="search-input sender-input"
          placeholder="收件人姓名"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select
          v-if="showLabelFilter && labelList.length"
          v-model="listQuery.labelId"
          placeholder="标签"
          clearable
          style="width: 120px"
          size="small"
          @change="search"
        >
          <el-option v-for="label in labelList" :key="label.id" :label="label.name" :value="Number(label.id)" />
        </el-select>
      </div>

      <div class="toolbar-right">
        <span class="mail-count">共 {{ total }} 封</span>
        <el-select v-if="showReadFilter" v-model="statusFilter" placeholder="阅读状态" style="width: 118px" size="small" @change="changeStatusFilter">
          <el-option label="所有" value="all" />
          <el-option label="未读" value="0" />
          <el-option label="已读" value="1" />
        </el-select>
      </div>
    </div>

    <div v-if="activeFilterTags.length" class="filter-tags">
      <span class="filter-tags-label">当前筛选：</span>
      <el-tag v-for="tag in activeFilterTags" :key="tag.key" size="small" closable @close="clearFilter(tag.key)">
        {{ tag.label }}
      </el-tag>
    </div>

    <div v-if="groupedList.length" class="qq-mail-list">
      <section v-for="group in groupedList" :key="group.title" class="mail-group">
        <div class="group-header">{{ group.title }}（{{ group.count }}）</div>
        <div
          v-for="row in group.items"
          :key="row.id"
          class="mail-row"
          :class="{ unread: row.status === 0, selected: isSelected(row) }"
          draggable="true"
          @click="openDetail(row.id)"
          @contextmenu.prevent="openContextMenu($event, row)"
          @dragstart="event => handleDragStart(event, row)"
        >
          <el-checkbox :model-value="isSelected(row)" class="row-check" @click.stop @change="val => toggleRow(row, val)" />
          <el-icon class="row-icon" :class="{ 'is-unread': row.status === 0 }"><Message /></el-icon>
          <div class="row-sender">{{ formatRowName(row) }}</div>
          <div class="row-content">
            <span class="row-subject">{{ row.title || '（无主题）' }}</span>
            <span v-if="row.preview" class="row-snippet"> - {{ row.preview }}</span>
            <el-tag v-for="label in row.labelList" :key="label.guid || label.id" size="small" class="row-tag" :style="{ borderColor: label.color, color: label.color }">
              {{ label.name }}
            </el-tag>
            <el-tag v-if="row.priorityLevel" size="small" class="row-priority-tag" :type="priorityTagType(row.priorityLevel)">
              {{ priorityLabel(row.priorityLevel) }}
            </el-tag>
            <span v-if="row.spamReason" class="row-spam-reason" :title="row.spamReason">
              {{ formatSpamReason(row.spamReason) }}
            </span>
          </div>
          <el-icon v-if="row.isHaveFile" class="row-attach"><Paperclip /></el-icon>
          <span class="row-date">{{ formatMailDate(row[dateField] || row.receiveDate || row.sendDate || row.date) }}</span>

          <div class="row-actions" @click.stop @mousedown.stop>
            <el-tooltip v-if="showAi" content="智能助手" placement="left" effect="dark">
              <el-icon class="row-action-icon" @click.stop="openAi(row)">
                <ChatDotRound />
              </el-icon>
            </el-tooltip>

            <el-dropdown
              v-if="showMark && labelList.length"
              trigger="click"
              placement="bottom-end"
              teleported
              @command="labelId => markRow(row, labelId)"
            >
              <span class="row-action-icon" @click.stop @mousedown.stop>
                <el-tooltip content="添加标签" placement="left" effect="dark">
                  <el-icon><CollectionTag /></el-icon>
                </el-tooltip>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="star">设为星标邮件</el-dropdown-item>
                  <el-dropdown-item v-for="label in labelList" :key="label.id" :command="String(label.id)">
                    {{ label.name }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <el-tooltip
              v-if="(!isTrash && deleteMail) || (isTrash && deletePermanentlyMail)"
              :content="isTrash ? '彻底删除' : '移入回收站'"
              placement="left"
              effect="dark"
            >
              <el-icon class="row-action-icon danger" @click.stop="deleteRow(row)">
                <Delete />
              </el-icon>
            </el-tooltip>

            <el-tooltip v-if="showStar" content="星标" placement="left" effect="dark">
              <el-icon class="row-star" :class="{ active: row.isStar }" @click.stop="toggleStar(row)">
                <StarFilled v-if="row.isStar" />
                <Star v-else />
              </el-icon>
            </el-tooltip>
          </div>
        </div>
      </section>
    </div>

    <div
      v-if="contextMenu.visible"
      class="mail-context-menu"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      @click.stop
    >
      <button type="button" class="context-menu-item" @click="handleContextDetail">
        {{ openMode === 'draft' ? '继续编辑' : '查看详情' }}
      </button>
      <button v-if="isTrash && restoreMail" type="button" class="context-menu-item" @click="handleContextRestore">恢复</button>
      <button v-if="isTrash && deletePermanentlyMail" type="button" class="context-menu-item danger" @click="handleContextPermanentDelete">彻底删除</button>
      <button v-if="!isTrash && deleteMail" type="button" class="context-menu-item" @click="handleContextDelete">移入回收站</button>
      <button v-if="showAi" type="button" class="context-menu-item" @click="handleContextAi">打开智能助手</button>
      <button v-if="showAi" type="button" class="context-menu-item" @click="handleContextAiSummary">生成摘要</button>
      <button v-if="showAi" type="button" class="context-menu-item" @click="handleContextAiReply">回复建议</button>
      <button v-if="showAi" type="button" class="context-menu-item" @click="handleContextAiActionItems">提取待办</button>
      <button v-if="showNotSpam" type="button" class="context-menu-item" @click="handleContextNotSpam">恢复到收件箱</button>
      <div v-if="showMark && labelList.length && !isTrash" class="context-menu-group">
        <div class="context-menu-label">添加标签</div>
        <button type="button" class="context-menu-item" @click="handleContextApplyLabel('star')">设为星标邮件</button>
        <button v-for="label in labelList" :key="label.id" type="button" class="context-menu-item" @click="handleContextApplyLabel(String(label.id))">
          <span class="label-dot" :style="{ background: label.color }" />
          {{ label.name }}
        </button>
      </div>
    </div>

    <el-empty v-else-if="!loading" :description="emptyText" class="qq-empty" />

    <div class="qq-pagination">
      <el-pagination
        v-model:current-page="listQuery.page"
        v-model:page-size="listQuery.limit"
        :total="total"
        :page-sizes="[20, 30, 50]"
        layout="total, sizes, prev, pager, next"
        background
        small
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowDown, ChatDotRound, CollectionTag, Delete, Message, Paperclip, Search, Star, StarFilled } from '@element-plus/icons-vue';
import { useMailList } from '@/composables/useMailList';
import { useMailStore } from '@/stores/mail';
import { useAiStore } from '@/stores/ai';
import { useNotificationStore } from '@/stores/notification';
import { formatMailDate, groupMailsByDate } from '@/utils/mailGroup';
import MailMarkDropdown from '@/components/MailMarkDropdown.vue';
import SpamFilterSwitch from '@/components/SpamFilterSwitch.vue';
import PriorityFilterSwitch from '@/components/PriorityFilterSwitch.vue';

const props = defineProps({
  folderTitle: { type: String, default: '收件箱' },
  emptyText: { type: String, default: '暂无邮件' },
  fetchList: { type: Function, required: true },
  deleteMail: { type: Function, default: null },
  restoreMail: { type: Function, default: null },
  deletePermanentlyMail: { type: Function, default: null },
  deleteConfirmText: { type: String, default: '确定将选中的 {count} 封邮件移入回收站吗？' },
  cacheKey: { type: String, default: '' },
  isTrash: { type: Boolean, default: false },
  listParams: { type: Object, default: () => ({}) },
  mailType: { type: String, default: 'receive' },
  openMode: { type: String, default: 'detail' },
  rowDisplay: { type: String, default: 'sender' },
  dateField: { type: String, default: 'receiveDate' },
  showForward: { type: Boolean, default: true },
  showEdit: { type: Boolean, default: false },
  showMarkAllRead: { type: Boolean, default: false },
  showReport: { type: Boolean, default: false },
  showNotSpam: { type: Boolean, default: false },
  showSpamFilterSwitch: { type: Boolean, default: false },
  showPriorityFilterSwitch: { type: Boolean, default: false },
  showMoveTo: { type: Boolean, default: false },
  showMark: { type: Boolean, default: true },
  showReadFilter: { type: Boolean, default: true },
  showSenderFilter: { type: Boolean, default: true },
  showRecipientFilter: { type: Boolean, default: false },
  showLabelFilter: { type: Boolean, default: true },
  showStar: { type: Boolean, default: true },
  showAi: { type: Boolean, default: true },
  onMarkAllRead: { type: Function, default: null },
  onReportSpam: { type: Function, default: null },
  onNotSpam: { type: Function, default: null }
});

const emit = defineEmits(['loaded']);
const route = useRoute();
const router = useRouter();
const mailStore = useMailStore();
const aiStore = useAiStore();
const notificationStore = useNotificationStore();
const contextMenu = reactive({ visible: false, x: 0, y: 0, row: null });

function wrappedFetch(params) {
  return props.fetchList({ ...params, ...props.listParams });
}

const {
  list, total, loading, labelList, multipleSelection, listQuery,
  hydrateCache, hydrateQueryCache, loadList, loadLabels,
  handleDelete, handleRestore, handlePermanentDelete, toggleStar, handleMark
} = useMailList(wrappedFetch, props.deleteMail, {
  cacheKey: props.cacheKey,
  restoreApi: props.restoreMail,
  permanentDeleteApi: props.deletePermanentlyMail
});

listQuery.limit = 50;

const selectAll = ref(false);
const statusFilter = ref('all');
const groupedList = computed(() => groupMailsByDate(list.value, props.dateField));
const indeterminate = computed(() => {
  const count = multipleSelection.value.length;
  return count > 0 && count < list.value.length;
});
const nameFilterKey = computed(() => (props.rowDisplay === 'recipient' ? '收件人' : '发件人'));

const activeFilterTags = computed(() => {
  const tags = [];
  if (listQuery.title) tags.push({ key: 'title', label: `关键词：${listQuery.title}` });
  if (listQuery.receiveName) tags.push({ key: 'receiveName', label: `${nameFilterKey.value}姓名：${listQuery.receiveName}` });
  if (listQuery.receiveMail && props.showSenderFilter) tags.push({ key: 'receiveMail', label: `发件人邮箱：${listQuery.receiveMail}` });
  if (listQuery.labelId != null && listQuery.labelId !== '') {
    const label = labelList.value.find(item => Number(item.id) === Number(listQuery.labelId));
    tags.push({ key: 'labelId', label: `标签：${label?.name || listQuery.labelId}` });
  }
  if (listQuery.status === 0 || listQuery.status === 1) tags.push({ key: 'status', label: listQuery.status === 0 ? '未读' : '已读' });
  return tags;
});

const formatSpamReason = reason => (!reason ? '' : reason.length > 48 ? `${reason.slice(0, 48)}...` : reason);
const priorityLabel = level => ({ HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }[level] || level);
const priorityTagType = level => (level === 'HIGH' ? 'danger' : level === 'LOW' ? 'info' : 'warning');

function formatRowName(row) {
  if (props.rowDisplay === 'recipient') {
    const receivers = row.receiveList || [];
    if (!receivers.length) return '（无收件人）';
    return receivers.map(item => item.name || item.mail).join('、');
  }
  return row.sendName || row.sendMail || '（未知发件人）';
}

watch(multipleSelection, rows => {
  selectAll.value = list.value.length > 0 && rows.length === list.value.length;
});

watch(() => [route.query.title, route.query.labelId], () => {
  syncQueryFromRoute();
  listQuery.page = 1;
  loadList();
});

watch(() => props.listParams, () => {
  syncListParams();
  listQuery.page = 1;
  loadList();
}, { deep: true });

onMounted(async () => {
  hydrateQueryCache();
  syncQueryFromRoute();
  syncListParams();
  hydrateCache();
  syncStatusFilter();
  await Promise.all([loadList(), loadLabels()]);
  window.addEventListener('click', hideContextMenu);
  window.addEventListener('blur', hideContextMenu);
  window.addEventListener('resize', hideContextMenu);
});

onBeforeUnmount(() => {
  window.removeEventListener('click', hideContextMenu);
  window.removeEventListener('blur', hideContextMenu);
  window.removeEventListener('resize', hideContextMenu);
});

watch(total, () => emit('loaded', { total: total.value }));
watch(() => [notificationStore.inboxTick, notificationStore.highPriorityTick], () => {
  if (props.mailType === 'receive' && !props.isTrash && !props.showNotSpam) loadList();
});
watch(() => notificationStore.spamTick, () => {
  if (props.showNotSpam) loadList();
});

function syncQueryFromRoute() {
  listQuery.title = route.query.title ? String(route.query.title) : undefined;
  listQuery.labelId = route.query.labelId ? Number(route.query.labelId) : undefined;
}

function syncListParams() {
  const params = props.listParams || {};
  if (params.labelId != null) listQuery.labelId = Number(params.labelId);
}

function syncStatusFilter() {
  if (listQuery.status === 0) statusFilter.value = '0';
  else if (listQuery.status === 1) statusFilter.value = '1';
  else statusFilter.value = 'all';
}

function changeStatusFilter(value) {
  listQuery.status = value === 'all' ? undefined : Number(value);
  search();
}

function clearFilter(key) {
  if (key === 'title') listQuery.title = undefined;
  else if (key === 'receiveName') listQuery.receiveName = undefined;
  else if (key === 'receiveMail') listQuery.receiveMail = undefined;
  else if (key === 'labelId') {
    if (props.listParams?.labelId != null) return;
    listQuery.labelId = undefined;
  } else if (key === 'status') {
    listQuery.status = undefined;
    statusFilter.value = 'all';
  }
  search();
}

const isSelected = row => multipleSelection.value.some(item => item.id === row.id);

function toggleRow(row, checked) {
  if (checked) {
    if (!isSelected(row)) multipleSelection.value = [...multipleSelection.value, row];
    return;
  }
  multipleSelection.value = multipleSelection.value.filter(item => item.id !== row.id);
}

const onSelectAll = value => {
  multipleSelection.value = value ? [...list.value] : [];
};
const search = () => {
  listQuery.page = 1;
  loadList();
};
const refresh = () => {
  loadList();
  loadLabels();
};

function openDetail(id) {
  if (props.openMode === 'draft') {
    mailStore.setDraftId(id);
    mailStore.setMailId(null);
    mailStore.setPageType('edit');
    mailStore.setMailType('draft');
    router.push('/mail_send');
    return;
  }
  mailStore.setMailId(id);
  mailStore.setMailType(props.mailType);
  router.push('/mail_detail');
}

async function doDelete() {
  await handleDelete(props.deleteConfirmText);
}

async function deleteRow(row) {
  multipleSelection.value = [row];
  if (props.isTrash) await handlePermanentDelete();
  else await doDelete();
}

function aiContextForRow(row) {
  return { id: row.id, title: row.title, sender: row.sendName || row.sendMail, senderMail: row.sendMail, mailType: props.mailType };
}

const openAi = row => aiStore.openForMail(aiContextForRow(row));
const openAiWithAction = (row, action) => aiStore.openForMail(aiContextForRow(row), { action });

async function markRow(row, labelId) {
  multipleSelection.value = [row];
  await handleMark(labelId);
}

function handleDragStart(event, row) {
  event.dataTransfer?.setData('application/x-mail-context', JSON.stringify(aiContextForRow(row)));
  event.dataTransfer?.setData('text/plain', row.title || row.sendName || row.sendMail || '邮件');
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy';
}

function openContextMenu(event, row) {
  contextMenu.visible = true;
  contextMenu.row = row;
  contextMenu.x = Math.min(event.clientX, window.innerWidth - 220);
  contextMenu.y = Math.min(event.clientY, window.innerHeight - 240);
}

function hideContextMenu() {
  contextMenu.visible = false;
  contextMenu.row = null;
}

const handleContextDetail = () => { if (contextMenu.row) openDetail(contextMenu.row.id); hideContextMenu(); };
const handleContextAi = () => { if (contextMenu.row) openAi(contextMenu.row); hideContextMenu(); };
const handleContextAiSummary = () => { if (contextMenu.row) openAiWithAction(contextMenu.row, 'summary'); hideContextMenu(); };
const handleContextAiReply = () => { if (contextMenu.row) openAiWithAction(contextMenu.row, 'reply'); hideContextMenu(); };
const handleContextAiActionItems = () => { if (contextMenu.row) openAiWithAction(contextMenu.row, 'action-items'); hideContextMenu(); };

async function handleContextApplyLabel(labelId) {
  if (!contextMenu.row) return;
  multipleSelection.value = [contextMenu.row];
  hideContextMenu();
  await handleMark(labelId);
}

const selectContextRow = () => { if (contextMenu.row) multipleSelection.value = [contextMenu.row]; };
const handleContextRestore = async () => { selectContextRow(); hideContextMenu(); await handleRestore(); };
const handleContextPermanentDelete = async () => { selectContextRow(); hideContextMenu(); await handlePermanentDelete(); };
const handleContextDelete = async () => { selectContextRow(); hideContextMenu(); await doDelete(); };
const handleContextNotSpam = async () => { selectContextRow(); hideContextMenu(); await markNotSpam(); };

function goCompose(pageType) {
  if (multipleSelection.value.length !== 1) {
    ElMessage.warning('请先只选择一封邮件');
    return;
  }
  const row = multipleSelection.value[0];
  mailStore.setMailId(row.id);
  mailStore.setPageType(pageType);
  mailStore.setMailType(props.mailType);
  router.push('/mail_send');
}

async function markAllRead() {
  if (!props.onMarkAllRead) return;
  await props.onMarkAllRead();
  ElMessage.success('已全部标为已读');
  refresh();
}

async function reportSpam() {
  const ids = multipleSelection.value.map(item => item.id);
  if (!ids.length) {
    ElMessage.warning('请先选择邮件');
    return;
  }
  await ElMessageBox.confirm(`确定将选中的 ${ids.length} 封邮件标记为垃圾邮件吗？`, '提示', { type: 'warning' });
  if (props.onReportSpam) {
    await props.onReportSpam(ids);
    ElMessage.success('已移入垃圾邮件');
    refresh();
  }
}

async function markNotSpam() {
  const ids = multipleSelection.value.map(item => item.id);
  if (!ids.length) {
    ElMessage.warning('请先选择邮件');
    return;
  }
  await ElMessageBox.confirm(`确定将选中的 ${ids.length} 封邮件恢复到收件箱吗？`, '提示', { type: 'info' });
  if (props.onNotSpam) {
    await props.onNotSpam(ids);
    ElMessage.success('已恢复到收件箱');
    refresh();
  }
}

async function moveToLabel(labelId) {
  if (!multipleSelection.value.length) {
    ElMessage.warning('请先选择邮件');
    return;
  }
  await handleMark(labelId);
}

defineExpose({ refresh });
</script>

<style scoped lang="scss">
.qq-inbox { height: 100%; display: flex; flex-direction: column; background: #fff; }
.filter-tags {
  display: flex; align-items: center; flex-wrap: wrap; gap: 6px;
  padding: 6px 16px; border-bottom: 1px solid #f0f0f0; background: #fafbfc;
  .filter-tags-label { font-size: 12px; color: #909399; }
}
.qq-toolbar {
  display: flex; align-items: center; justify-content: space-between; padding: 8px 16px;
  border-bottom: 1px solid #f0f0f0; flex-shrink: 0;
  .toolbar-left { display: flex; align-items: center; gap: 4px; flex: 1; min-width: 0; flex-wrap: wrap; }
  .folder-title { font-size: 15px; font-weight: 600; color: #303133; margin: 0 8px 0 4px; white-space: nowrap; }
  .search-input { width: 160px; margin-left: 4px; }
  .sender-input { width: 140px; }
  .toolbar-right { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
  .mail-count { font-size: 13px; color: #909399; white-space: nowrap; }
}
.qq-mail-list { flex: 1; overflow-y: auto; }
.mail-group .group-header { padding: 8px 16px 4px; font-size: 12px; color: #909399; background: #fafafa; border-bottom: 1px solid #f5f5f5; }
.mail-row {
  display: flex; align-items: center; gap: 8px; padding: 0 16px; height: 44px; border-bottom: 1px solid #f5f5f5; cursor: pointer; transition: background 0.15s;
  &:hover { background: #f5f9ff; }
  &.selected { background: #ecf5ff; }
  &.unread .row-sender, &.unread .row-subject { font-weight: 600; color: #303133; }
  .row-sender { flex-shrink: 0; width: 140px; font-size: 14px; color: #606266; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .row-content { flex: 1; min-width: 0; display: flex; align-items: center; overflow: hidden; white-space: nowrap; }
  .row-subject { flex-shrink: 0; max-width: 40%; font-size: 14px; color: #303133; overflow: hidden; text-overflow: ellipsis; }
  .row-snippet { font-size: 13px; color: #909399; overflow: hidden; text-overflow: ellipsis; }
  .row-priority-tag { margin-left: 4px; vertical-align: middle; }
  .row-spam-reason { display: block; font-size: 11px; color: #e6a23c; margin-top: 2px; line-height: 1.3; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .row-tag { margin-left: 6px; flex-shrink: 0; background: transparent; }
  .row-date { flex-shrink: 0; width: 88px; text-align: right; font-size: 12px; color: #909399; }
  .row-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
  .row-action-icon, .row-star { flex-shrink: 0; font-size: 16px; color: #9aa6b2; cursor: pointer; &:hover { color: #1a73e8; } }
  .row-action-icon.danger:hover { color: #f56c6c; }
  .row-star { color: #dcdfe6; &.active, &:hover { color: #e6a23c; } }
  .row-icon.is-unread { color: #1a73e8; }
}
.qq-empty { flex: 1; }
.qq-pagination { padding: 8px 16px; border-top: 1px solid #f0f0f0; display: flex; justify-content: flex-end; flex-shrink: 0; }
.mail-context-menu { position: fixed; z-index: 3000; min-width: 168px; padding: 8px 0; background: #fff; border: 1px solid #e6ebf5; border-radius: 12px; box-shadow: 0 16px 30px rgba(31, 45, 61, 0.18); }
.context-menu-group { border-top: 1px solid #eef2f7; margin-top: 6px; padding-top: 6px; }
.context-menu-label { padding: 4px 16px; font-size: 12px; color: #909399; }
.label-dot { display: inline-block; width: 8px; height: 8px; border-radius: 999px; margin-right: 8px; }
.context-menu-item.danger { color: #f56c6c; }
.context-menu-item {
  display: block; width: 100%; padding: 10px 16px; border: none; background: transparent;
  text-align: left; font-size: 14px; color: #303133; cursor: pointer;
  &:hover { background: #f5f9ff; color: #1a73e8; }
}
</style>
