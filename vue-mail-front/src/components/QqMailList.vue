<template>
  <div v-loading="loading" class="qq-inbox" @click="hideContextMenu">
    <div class="qq-toolbar">
      <div class="toolbar-left">
        <el-checkbox v-model="selectAll" :indeterminate="indeterminate" @change="onSelectAll" />
        <span class="folder-title">{{ folderTitle }}</span>

        <!-- Selection Actions: Only show when multipleSelection.length > 0 -->
        <template v-if="multipleSelection.length > 0">
          <el-button v-if="isTrash && restoreMail" text size="small" class="toolbar-btn" @click="handleRestore()">恢复</el-button>
          <el-button v-if="isTrash && deletePermanentlyMail" text size="small" class="toolbar-btn danger" @click="handlePermanentDelete()">彻底删除</el-button>
          <el-button v-if="!isTrash && deleteMail" text size="small" class="toolbar-btn danger" @click="doDelete()">删除</el-button>
          <el-button v-if="showEdit && multipleSelection.length === 1" text size="small" class="toolbar-btn" @click="goCompose('edit')">编辑</el-button>
          <el-button v-if="showForward && multipleSelection.length === 1" text size="small" class="toolbar-btn" @click="goCompose('forward')">转发</el-button>
          <el-button v-if="showReport" text size="small" class="toolbar-btn" @click="reportSpam">标记为垃圾邮件</el-button>
          <el-button v-if="showNotSpam" text size="small" class="toolbar-btn" @click="markNotSpam">恢复到收件箱</el-button>
          <el-dropdown v-if="showMoveTo && labelList.length" trigger="click" @command="moveToLabel">
            <el-button text size="small" class="toolbar-btn">
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
        </template>

        <!-- General Actions: Show when no selection -->
        <template v-else>
          <SpamFilterSwitch v-if="showSpamFilterSwitch" />
          <PriorityFilterSwitch v-if="showPriorityFilterSwitch" />
          <el-button text size="small" class="toolbar-btn" @click="refresh">刷新</el-button>
          <el-button v-if="showMarkAllRead" text size="small" class="toolbar-btn" @click="markAllRead">全部标为已读</el-button>
          
          <!-- Filter Button and Popover -->
          <el-popover placement="bottom-start" title="高级筛选" :width="340" trigger="click">
            <template #reference>
              <el-button text size="small" class="toolbar-btn">
                <el-icon class="el-icon--left"><Filter /></el-icon>
                筛选
              </el-button>
            </template>
            <div class="advanced-filter-panel">
              <el-form label-width="90px" size="default">
                <el-form-item label="主题/正文">
                  <el-input v-model="listQuery.title" placeholder="输入关键词" clearable @keyup.enter="search" />
                </el-form-item>
                <el-form-item v-if="showSenderFilter" label="发件人姓名">
                  <el-input v-model="listQuery.receiveName" placeholder="发件人姓名" clearable @keyup.enter="search" />
                </el-form-item>
                <el-form-item v-if="showSenderFilter" label="发件人邮箱">
                  <el-input v-model="listQuery.receiveMail" placeholder="发件人邮箱" clearable @keyup.enter="search" />
                </el-form-item>
                <el-form-item v-if="showRecipientFilter" label="收件人姓名">
                  <el-input v-model="listQuery.receiveName" placeholder="收件人姓名" clearable @keyup.enter="search" />
                </el-form-item>
                <el-form-item v-if="showLabelFilter && labelList.length" label="标签">
                  <el-select v-model="listQuery.labelId" placeholder="选择标签" clearable style="width: 100%" @change="search">
                    <el-option v-for="label in labelList" :key="label.id" :label="label.name" :value="Number(label.id)" />
                  </el-select>
                </el-form-item>
                <div class="filter-footer-actions">
                  <el-button size="small" @click="resetFilters">重置</el-button>
                  <el-button type="primary" size="small" @click="search">筛选</el-button>
                </div>
              </el-form>
            </div>
          </el-popover>
        </template>
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
          @click="openDetail(row)"
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

    <el-empty v-if="!loading && !list.length" :description="emptyTitle" class="qq-empty" :image-size="96">
      <template #image>
        <div class="empty-illustration">
          <el-icon><Message /></el-icon>
        </div>
      </template>
      <p v-if="emptyDescription" class="empty-description">{{ emptyDescription }}</p>
    </el-empty>

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
import { ArrowDown, ChatDotRound, CollectionTag, Delete, Message, Paperclip, Search, Star, StarFilled, Filter } from '@element-plus/icons-vue';
import { mailDetailApi } from '@/api/mail';
import { useMailList } from '@/composables/useMailList';
import { useMailStore } from '@/stores/mail';
import { useAiStore } from '@/stores/ai';
import { useNotificationStore } from '@/stores/notification';
import { formatMailDate, groupMailsByDate } from '@/utils/mailGroup';
import { clearMailListCaches } from '@/utils/mailCache';
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
  permanentDeleteApi: props.deletePermanentlyMail,
  boxType: operationBoxType(),
  resolveBoxType: row => operationBoxType(row)
});

listQuery.limit = 50;

const selectAll = ref(false);
const statusFilter = ref('all');
const groupedList = computed(() => groupMailsByDate(list.value, props.dateField));
const emptyTitle = computed(() => props.emptyText || '暂无邮件');
const emptyDescription = computed(() => {
  if (activeFilterTags.value.length) return '没有找到符合条件的邮件，可以清空筛选或刷新后再试。';
  return '';
});
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

function operationBoxType(row) {
  if (props.isTrash && !row) return undefined;
  const type = row?.type || props.mailType;
  if (type === 'send') return 'OUTBOX';
  if (type === 'draft') return 'DRAFT';
  if (type === 'receive') return 'INBOX';
  if (props.mailType === 'send') return 'OUTBOX';
  if (props.mailType === 'draft') return 'DRAFT';
  if (props.mailType === 'receive') return 'INBOX';
  return undefined;
}

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
watch(() => notificationStore.readTick, () => {
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

function resetFilters() {
  listQuery.title = undefined;
  listQuery.receiveName = undefined;
  listQuery.receiveMail = undefined;
  listQuery.labelId = undefined;
  search();
}

async function openDetail(row) {
  const id = row?.id;
  if (!id) return;
  if (props.openMode === 'draft') {
    mailStore.setDraftId(id);
    mailStore.setMailId(null);
    mailStore.setPageType('edit');
    mailStore.setMailType('draft');
    router.push('/mail_send');
    return;
  }
  await markRowReadBeforeOpen(row);
  mailStore.setMailId(id);
  mailStore.setMailType(props.mailType);
  router.push('/mail_detail');
}

async function markRowReadBeforeOpen(row) {
  if (!shouldMarkRowRead(row)) {
    return;
  }
  row.status = 1;
  clearMailListCaches();
  try {
    await mailDetailApi.markRead(row.id);
    notificationStore.onReadStateChanged();
  } catch (error) {
    row.status = 0;
    ElMessage.warning('标记已读失败，详情页会再次尝试同步。');
  }
}

function shouldMarkRowRead(row) {
  return props.mailType === 'receive'
    && !props.isTrash
    && !props.showNotSpam
    && Number(row?.status) === 0;
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

const handleContextDetail = () => { if (contextMenu.row) openDetail(contextMenu.row); hideContextMenu(); };
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
.qq-inbox { height: 100%; display: flex; flex-direction: column; background: var(--color-bg-card); }
.filter-tags {
  display: flex; align-items: center; flex-wrap: wrap; gap: 6px;
  padding: 8px 18px; border-bottom: 1px solid var(--color-border); background: var(--color-bg-page);
  .filter-tags-label { font-size: 12px; color: var(--color-text-muted); }
}

.qq-toolbar {
  display: flex; align-items: center; justify-content: space-between; padding: 14px 18px;
  border-bottom: 1px solid var(--color-border); flex-shrink: 0; background: var(--color-bg-card);
  .toolbar-left { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; }
  .folder-title { font-size: 23px; font-weight: 700; color: var(--color-text-main); margin: 0 18px 0 8px; white-space: nowrap; }
  
  .toolbar-btn {
    color: var(--color-text-secondary);
    font-size: 13px;
    height: 32px;
    padding: 0 12px;
    border-radius: 10px;
    margin: 0;
    font-weight: 500;
    &:hover {
      background-color: var(--color-hover);
      color: var(--color-primary);
    }
    &.danger:hover {
      color: #e11d48;
      background-color: #fff1f2;
    }
  }
  
  .toolbar-right { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
  .mail-count { font-size: 13px; color: var(--color-text-secondary); white-space: nowrap; }
}

.advanced-filter-panel {
  padding: 8px 4px;
  .filter-footer-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 12px;
    border-top: 1px solid var(--color-border);
    padding-top: 12px;
  }
}

.qq-mail-list { flex: 1; overflow-y: auto; }
.mail-group .group-header { padding: 10px 18px 6px; font-size: 12px; color: var(--color-text-muted); background: var(--color-bg-page); border-bottom: 1px solid var(--color-border); }
.mail-row {
  display: flex; align-items: center; gap: 10px; padding: 0 18px; min-height: 48px; border-bottom: 1px solid var(--color-border); cursor: pointer; transition: background 0.15s, box-shadow 0.15s;
  background: var(--color-bg-card);
  &:hover { background: var(--color-hover); }
  &.selected { background: var(--color-primary-light); }
  
  &.unread {
    .row-sender, .row-subject { font-weight: 700; color: var(--color-text-main); }
    .row-date { font-weight: 700; color: var(--color-primary); }
  }
  
  .row-sender { flex-shrink: 0; width: 148px; font-size: 15px; font-weight: 600; color: var(--color-text-main); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .row-content { flex: 1; min-width: 0; display: flex; align-items: center; overflow: hidden; white-space: nowrap; }
  .row-subject { flex-shrink: 0; max-width: 42%; font-size: 15px; font-weight: 600; color: var(--color-text-main); overflow: hidden; text-overflow: ellipsis; }
  .row-snippet { font-size: 14px; color: var(--color-text-secondary); overflow: hidden; text-overflow: ellipsis; }
  .row-priority-tag { margin-left: 8px; vertical-align: middle; }
  .row-spam-reason { display: block; font-size: 11px; color: #b45309; margin-top: 2px; line-height: 1.3; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .row-tag { margin-left: 6px; flex-shrink: 0; background: #ffffff; border-radius: 999px; font-size: 12px; font-weight: 500; }
  .row-date { flex-shrink: 0; width: 88px; text-align: right; font-size: 12px; color: var(--color-text-secondary); }
  .row-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
  .row-action-icon, .row-star { flex-shrink: 0; font-size: 18px; color: var(--color-text-secondary); cursor: pointer; &:hover { color: var(--color-primary); } }
  .row-action-icon.danger:hover { color: #e11d48; }
  .row-star { color: #cbd5e1; &.active, &:hover { color: #f59e0b; } }
  .row-icon { color: var(--color-text-muted); }
  .row-icon.is-unread { color: var(--color-primary); }
}
.qq-empty { flex: 1; display: flex; flex-direction: column; justify-content: center; }
.empty-illustration {
  width: 96px;
  height: 96px;
  border-radius: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #eef2ff 0%, #f8fafc 100%);
  color: var(--color-primary);
  border: 1px solid var(--color-border);
  .el-icon { font-size: 42px; }
}
.empty-description {
  margin: -4px 0 16px;
  color: var(--color-text-secondary);
  font-size: 14px;
}
.qq-pagination { padding: 10px 18px; border-top: 1px solid var(--color-border); display: flex; justify-content: flex-end; flex-shrink: 0; background: var(--color-bg-card); }
.mail-context-menu { position: fixed; z-index: 3000; min-width: 168px; padding: 8px 0; background: #fff; border: 1px solid var(--color-border); border-radius: 12px; box-shadow: var(--shadow-float); }
.context-menu-group { border-top: 1px solid var(--color-border); margin-top: 6px; padding-top: 6px; }
.context-menu-label { padding: 4px 16px; font-size: 12px; color: var(--color-text-muted); }
.label-dot { display: inline-block; width: 8px; height: 8px; border-radius: 999px; margin-right: 8px; }
.context-menu-item.danger { color: #e11d48; }
.context-menu-item {
  display: block; width: 100%; padding: 10px 16px; border: none; background: transparent;
  text-align: left; font-size: 14px; color: var(--color-text-main); cursor: pointer;
  &:hover { background: var(--color-hover); color: var(--color-primary); }
}
</style>
