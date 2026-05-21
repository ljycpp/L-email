<template>
  <div v-loading="loading" class="app-container">
    <div class="filter-container">
      <el-button @click="goBack">返回列表</el-button>
      <el-button v-if="mailType === 'receive'" type="primary" @click="goCompose('reply')">回复</el-button>
      <el-button v-if="mailType === 'receive'" @click="goCompose('replyAll')">回复全部</el-button>
      <el-button v-if="mailType === 'send'" type="primary" @click="goCompose('edit')">编辑</el-button>
      <el-button @click="goCompose('forward')">转发</el-button>
      <el-button type="success" plain @click="openAiAssistant">智能助手</el-button>
      <el-button v-if="mail.spamReason" type="success" @click="markNotSpam">恢复到收件箱</el-button>
      <el-button v-else type="danger" @click="remove">移入回收站</el-button>
      <el-button @click="load">刷新</el-button>
      <MailMarkDropdown :labels="labelList" @mark="markMail" />
    </div>

    <el-card>
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
      <div class="title-row">
        <h2>{{ mail.title || '（无主题）' }}</h2>
        <el-icon class="star" @click="toggleStar">
          <StarFilled v-if="mail.isStar" />
          <Star v-else />
        </el-icon>
      </div>
      <p><el-tag size="small">发件人</el-tag> {{ mail.sender }} &lt;{{ mail.sendMail }}&gt;</p>
      <p><el-tag size="small">时间</el-tag> {{ formatTime(showTime) }}</p>
      <p v-if="mail.labelList?.length" class="label-row">
        <el-tag size="small">标签</el-tag>
        <el-tag
          v-for="label in mail.labelList"
          :key="label.id"
          size="small"
          class="mail-label-tag"
          :style="{ borderColor: label.color, color: label.color }"
        >
          {{ label.name }}
        </el-tag>
      </p>
      <p>
        <el-tag size="small">收件人</el-tag>
        <span v-for="party in mail.target" :key="party.mail">{{ party.name }} &lt;{{ party.mail }}&gt;；</span>
      </p>
      <p v-if="mail.copy?.length">
        <el-tag size="small">抄送</el-tag>
        <span v-for="party in mail.copy" :key="party.mail">{{ party.name }} &lt;{{ party.mail }}&gt;；</span>
      </p>

      <MailAttachmentList :files="mail.oldFileList" />

      <div class="mail-content" v-html="mail.content" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Star, StarFilled } from '@element-plus/icons-vue';
import { labelApi, mailActionApi, mailDetailApi } from '@/api/mail';
import MailAttachmentList from '@/components/MailAttachmentList.vue';
import MailMarkDropdown from '@/components/MailMarkDropdown.vue';
import { useAiStore } from '@/stores/ai';
import { useMailStore } from '@/stores/mail';
import { formatTime } from '@/utils/format';
import { resolveApiUrl } from '@/utils/url';
import { getMailDetailCache, setMailDetailCache } from '@/utils/mailCache';

const router = useRouter();
const mailStore = useMailStore();
const aiStore = useAiStore();
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
    applyMailData(mailId, data);
    setMailDetailCache(mailType.value, mailId, data);
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
  await mailDetailApi.delete([mail.id]);
  ElMessage.success('已移入回收站。');
  router.push(mailType.value === 'send' ? '/outbox' : '/inbox');
}

async function toggleStar() {
  await labelApi.toggleStar([mail.id]);
  mail.isStar = !mail.isStar;
}

async function markMail(labelId) {
  if (labelId === 'star') {
    await toggleStar();
    return;
  }
  await labelApi.mark(labelId, [mail.id]);
  ElMessage.success('标签已更新。');
  load();
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

<style scoped>
.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.star {
  cursor: pointer;
  color: #e6a23c;
  font-size: 22px;
}
.mail-content {
  margin-top: 20px;
  padding: 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  min-height: 200px;
}
.label-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.mail-label-tag {
  background: transparent;
}
.spam-reason-alert,
.priority-reason-alert {
  margin-bottom: 16px;
}
.priority-score {
  margin-left: 8px;
  color: #909399;
  font-size: 13px;
}
.priority-desc {
  margin: 8px 0 0;
  color: #606266;
}
</style>
