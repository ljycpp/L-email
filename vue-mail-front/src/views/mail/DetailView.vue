<template>
  <div v-loading="loading" class="mail-detail-container">
    <div class="detail-toolbar">
      <el-button text size="default" class="toolbar-btn" @click="goBack">
        <el-icon class="el-icon--left"><ArrowLeft /></el-icon>返回
      </el-button>
      
      <div class="toolbar-divider"></div>

      <el-button v-if="mailType === 'receive'" text size="default" class="toolbar-btn" @click="goCompose('reply')">
        <el-icon class="el-icon--left"><Back /></el-icon>回复
      </el-button>
      <el-button v-if="mailType === 'receive'" text size="default" class="toolbar-btn" @click="goCompose('replyAll')">
        <el-icon class="el-icon--left"><Back /></el-icon>回复全部
      </el-button>
      <el-button v-if="mailType === 'send'" text size="default" class="toolbar-btn" @click="goCompose('edit')">
        编辑
      </el-button>
      <el-button text size="default" class="toolbar-btn" @click="goCompose('forward')">
        <el-icon class="el-icon--left"><Share /></el-icon>转发
      </el-button>

      <div class="toolbar-divider"></div>

      <el-button text size="default" class="toolbar-btn ai-btn" @click="openAiAssistant">
        <el-icon class="el-icon--left"><ChatDotRound /></el-icon>智能助手
      </el-button>

      <div class="toolbar-divider"></div>

      <el-button v-if="mail.spamReason" text size="default" class="toolbar-btn" @click="markNotSpam">
        <el-icon class="el-icon--left"><MessageBox /></el-icon>恢复到收件箱
      </el-button>
      <el-button v-else text size="default" class="toolbar-btn danger" @click="remove">
        <el-icon class="el-icon--left"><Delete /></el-icon>移入回收站
      </el-button>

      <el-button text size="default" class="toolbar-btn" @click="load">
        <el-icon class="el-icon--left"><Refresh /></el-icon>刷新
      </el-button>

      <div class="toolbar-divider"></div>
      
      <MailMarkDropdown :labels="labelList" size="default" @mark="markMail" />
    </div>

    <div class="detail-content-area">
      <div class="email-main-body">
        <!-- Warnings / Info Alerts -->
        <el-alert
          v-if="mail.spamReason"
          type="warning"
          :closable="false"
          show-icon
          title="垃圾邮件识别说明"
          :description="mail.spamReason"
          class="spam-reason-alert"
        />
        
        <el-alert
          v-if="mail.priorityReason"
          type="info"
          :closable="false"
          show-icon
          title="优先级分析"
          class="priority-reason-alert"
        >
          <template #default>
            <el-tag v-if="mail.priorityLevel" size="small" :type="priorityTagType(mail.priorityLevel)">
              {{ priorityLabel(mail.priorityLevel) }}
            </el-tag>
            <span v-if="mail.priorityScore != null" class="priority-score">
              分数 {{ (mail.priorityScore * 100).toFixed(0) }}%
            </span>
            <p class="priority-desc">{{ mail.priorityReason }}</p>
          </template>
        </el-alert>

        <!-- Email Subject Row -->
        <div class="title-row">
          <h2 class="mail-subject">{{ mail.title || '（无主题）' }}</h2>
          <div class="mail-title-actions">
            <el-icon class="star" :class="{ active: mail.isStar }" @click="toggleStar">
              <StarFilled v-if="mail.isStar" />
              <Star v-else />
            </el-icon>
          </div>
        </div>

        <!-- Labels Display -->
        <div v-if="mail.labelList?.length" class="mail-labels-area">
          <span
            v-for="label in mail.labelList"
            :key="label.id"
            class="detail-label-tag"
            :style="{ backgroundColor: label.color + '15', borderColor: label.color, color: label.color }"
          >
            {{ label.name }}
          </span>
        </div>

        <!-- Sender / Receiver Block -->
        <div class="sender-info-block">
          <el-avatar :size="40" class="sender-avatar">{{ senderLetter }}</el-avatar>
          <div class="sender-details">
            <div class="sender-main-row">
              <span class="sender-name">{{ mail.sender || '未知' }}</span>
              <span class="sender-email">&lt;{{ mail.sendMail }}&gt;</span>
              <span class="mail-date-time">{{ formatTime(showTime) }}</span>
            </div>
            <div class="recipients-row">
              <span class="to-prefix">至</span>
              <span class="to-names" v-for="(party, idx) in mail.target" :key="party.mail">
                {{ party.name || party.mail }} &lt;{{ party.mail }}&gt;{{ idx < mail.target.length - 1 ? '、' : '' }}
              </span>
              <span v-if="mail.copy?.length" class="cc-section">
                ；抄送：
                <span v-for="(party, idx) in mail.copy" :key="party.mail">
                  {{ party.name || party.mail }} &lt;{{ party.mail }}&gt;{{ idx < mail.copy.length - 1 ? '、' : '' }}
                </span>
              </span>
            </div>
          </div>
        </div>

        <!-- Attachment List -->
        <MailAttachmentList :files="mail.oldFileList" class="attachments-area" />

        <!-- Mail Content Body -->
        <div class="mail-content" v-html="mail.content" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  Star, StarFilled, ArrowLeft, Back, Delete, Refresh, ChatDotRound, Share, MessageBox
} from '@element-plus/icons-vue';
import { labelApi, mailActionApi, mailDetailApi } from '@/api/mail';
import MailAttachmentList from '@/components/MailAttachmentList.vue';
import MailMarkDropdown from '@/components/MailMarkDropdown.vue';
import { useAiStore } from '@/stores/ai';
import { useMailStore } from '@/stores/mail';
import { useNotificationStore } from '@/stores/notification';
import { formatTime } from '@/utils/format';
import { resolveApiUrl } from '@/utils/url';
import { clearMailListCaches, getMailDetailCache, setMailDetailCache } from '@/utils/mailCache';

const router = useRouter();
const mailStore = useMailStore();
const aiStore = useAiStore();
const notificationStore = useNotificationStore();
const loading = ref(false);
const labelList = ref([]);
const mailType = ref(mailStore.mailType || 'receive');

const mail = reactive({
  id: null,
  title: '',
  content: '',
  sender: '',
  sendMail: '',
  target: [],
  copy: [],
  oldFileList: [],
  isStar: false,
  receiveDate: null,
  sendDate: null,
  labelList: [],
  spamReason: '',
  spamScore: null,
  autoFiltered: false,
  priorityLevel: '',
  priorityScore: null,
  priorityReason: '',
  priorityScored: false
});

const showTime = computed(() => mail.receiveDate || mail.sendDate);
const senderLetter = computed(() => {
  const name = mail.sender || mail.sendMail || 'U';
  return name.charAt(0).toUpperCase();
});

onMounted(async () => {
  await Promise.all([load(), loadLabels()]);
});

async function loadLabels() {
  const { data } = await labelApi.list();
  labelList.value = data.labelList || [];
}

function applyMailData(mailId, data) {
  Object.assign(mail, data, {
    oldFileList: (data.oldFileList || []).map(file => ({ ...file, url: resolveApiUrl(file.url) }))
  });
  mail.id = mailId;
}

async function load() {
  const mailId = mailStore.mailId;
  mailType.value = mailStore.mailType || 'receive';
  if (!mailId) {
    router.push('/inbox');
    return;
  }
  const cached = getMailDetailCache(mailType.value, mailId);
  if (cached) {
    applyMailData(mailId, cached);
  }
  loading.value = true;
  try {
    const { data } = await mailDetailApi.get({ mailId, mailType: mailType.value });
    if (isInboxDetail()) {
      await mailDetailApi.markRead(mailId);
    }
    applyMailData(mailId, data);
    setMailDetailCache(mailType.value, mailId, data);
    if (isInboxDetail()) {
      clearMailListCaches();
      notificationStore.onReadStateChanged();
    }
  } catch (error) {
    if (!cached) {
      throw error;
    }
    ElMessage.warning('网络请求失败，已显示本地缓存详情。');
  } finally {
    loading.value = false;
  }
}

function priorityLabel(level) {
  const map = { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' };
  return map[level] || level;
}

function priorityTagType(level) {
  if (level === 'HIGH') return 'danger';
  if (level === 'LOW') return 'info';
  return 'warning';
}

function goBack() {
  if (mail.spamReason) {
    router.push('/spam');
    return;
  }
  const backMap = { receive: '/inbox', send: '/outbox', draft: '/draftbox' };
  router.push(backMap[mailType.value] || '/inbox');
}

async function markNotSpam() {
  await mailActionApi.notSpam([mail.id]);
  ElMessage.success('已恢复到收件箱。');
  router.push('/inbox');
}

function goCompose(pageType) {
  mailStore.setMailId(mail.id);
  mailStore.setPageType(pageType);
  mailStore.setMailType(mailType.value);
  router.push('/mail_send');
}

async function remove() {
  await ElMessageBox.confirm('确定将这封邮件移入回收站吗？', '提示', { type: 'warning' });
  await mailDetailApi.delete([mail.id], detailBoxType());
  ElMessage.success('已移入回收站。');
  router.push(mailType.value === 'send' ? '/outbox' : '/inbox');
}

async function toggleStar() {
  await labelApi.toggleStar([mail.id], detailBoxType());
  mail.isStar = !mail.isStar;
}

async function markMail(labelId) {
  if (labelId === 'star') {
    await toggleStar();
    return;
  }
  await labelApi.mark(labelId, [mail.id], detailBoxType());
  ElMessage.success('标签已更新。');
  load();
}

function detailBoxType() {
  if (mailType.value === 'send') return 'OUTBOX';
  if (mailType.value === 'draft') return 'DRAFT';
  return 'INBOX';
}

function isInboxDetail() {
  return detailBoxType() === 'INBOX';
}

function openAiAssistant() {
  aiStore.openForMail({
    id: mail.id,
    title: mail.title,
    sender: mail.sender,
    senderMail: mail.sendMail,
    mailType: mailType.value
  });
}
</script>

<style scoped lang="scss">
.mail-detail-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-card);
}

.detail-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
  background: var(--color-bg-card);

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

    &.ai-btn {
      color: var(--color-primary);
      border: 1px solid var(--color-primary-light);
      background-color: #f8faff;
      
      &:hover {
        background-color: var(--color-primary-light);
        color: var(--color-primary-hover);
      }
    }
  }

  .toolbar-divider {
    width: 1px;
    height: 20px;
    background-color: var(--color-border);
    margin: 0 8px;
  }
}

.detail-content-area {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px;
  background: var(--color-bg-page);
}

.spam-reason-alert,
.priority-reason-alert {
  margin-bottom: 20px;
  border-radius: 8px;
}

.priority-score {
  margin-left: 8px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.priority-desc {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
}

.email-main-body {
  max-width: 900px;
  margin: 0 auto;
  padding: 28px 32px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  box-shadow: var(--shadow-soft);
}

.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
  
  .mail-subject {
    font-size: 24px;
    font-weight: 700;
    color: var(--color-text-main);
    margin: 0;
    line-height: 1.3;
  }
  
  .mail-title-actions {
    display: flex;
    align-items: center;
    padding-top: 4px;
  }
}

.star {
  cursor: pointer;
  color: #cbd5e1;
  font-size: 22px;
  transition: color 0.2s;
  
  &:hover, &.active {
    color: #f59e0b;
  }
}

.mail-labels-area {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 20px;
  
  .detail-label-tag {
    display: inline-block;
    padding: 3px 9px;
    font-size: 12px;
    border-radius: 999px;
    border: 1px solid;
    font-weight: 500;
  }
}

.sender-info-block {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
  
  .sender-avatar {
    background-color: var(--color-primary-light);
    color: var(--color-primary);
    font-weight: bold;
    font-size: 16px;
  }
  
  .sender-details {
    flex: 1;
    min-width: 0;
  }
  
  .sender-main-row {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-bottom: 4px;
    
    .sender-name {
      font-weight: 600;
      font-size: 15px;
      color: var(--color-text-main);
    }
    
    .sender-email {
      font-size: 12px;
      color: var(--color-text-secondary);
    }
    
    .mail-date-time {
      margin-left: auto;
      font-size: 12px;
      color: var(--color-text-secondary);
    }
  }
  
  .recipients-row {
    font-size: 13px;
    color: var(--color-text-secondary);
    line-height: 1.4;
    
    .to-prefix {
      color: var(--color-text-muted);
      margin-right: 4px;
    }
    
    .to-names {
      color: var(--color-text-main);
    }
    
    .cc-section {
      color: var(--color-text-muted);
      
      span {
        color: var(--color-text-main);
      }
    }
  }
}

.attachments-area {
  margin-bottom: 24px;
}

.mail-content {
  color: var(--color-text-main);
  font-size: 14px;
  line-height: 1.6;
  min-height: 300px;
  word-wrap: break-word;
  word-break: break-word;
}
</style>
