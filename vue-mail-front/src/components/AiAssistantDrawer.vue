<template>
  <div class="ai-assistant-panel">
    <div class="drawer-shell">
      <header class="assistant-header">
        <div class="header-top-row">
          <div class="assistant-eyebrow">{{ MAIL_SYSTEM_NAME }}</div>
          <el-button class="close-panel-btn" :icon="Close" circle text @click="aiStore.close()" />
        </div>
        <div class="header-title-area">
          <h3>智能助手</h3>
          <p class="assistant-subtitle">像 GPT 一样自由对话，也可对邮件生成摘要、提取待办或提供回复建议。</p>
        </div>
        <div class="header-actions">
          <el-button text size="small" @click="goSettings">设置</el-button>
          <el-button text size="small" @click="resetConversation">清空</el-button>
        </div>
      </header>

      <section
        class="context-card"
        :class="{ 'is-drop-target': dragActive }"
        @dragover.prevent="dragActive = true"
        @dragleave.prevent="dragActive = false"
        @drop.prevent="handleMailDrop"
      >
        <div class="context-label">当前邮件</div>
        <div class="context-title">{{ currentMail.title || '未选择邮件' }}</div>
        <div v-if="currentMail.sender || currentMail.senderMail" class="context-meta">
          {{ currentMail.sender || currentMail.senderMail }}
          <span v-if="currentMail.senderMail && currentMail.sender !== currentMail.senderMail">
            &lt;{{ currentMail.senderMail }}&gt;
          </span>
        </div>
        <div class="context-status">
          <el-tag size="small" :type="configured ? 'success' : 'warning'">
            {{ configured ? '已配置' : '待配置' }}
          </el-tag>
          <el-tag v-if="currentMail.id" size="small" type="info">邮件 #{{ currentMail.id }}</el-tag>
        </div>
        <div class="drop-tip">可将邮件列表中的邮件拖到此处，快速切换分析对象。</div>
      </section>

      <el-alert
        v-if="!configured"
        type="warning"
        :closable="false"
        title="请先在 AI 设置中配置 VVEAI 的接入密钥和模型名称。"
        class="drawer-alert"
      />

      <section v-if="recentResults.length" class="history-card">
        <div class="history-header">
          <span>本邮件最近结果</span>
          <el-button text size="small" @click="showHistory = !showHistory">
            {{ showHistory ? '收起' : '展开' }}
          </el-button>
        </div>
        <div v-if="showHistory" class="history-list">
          <div v-for="item in recentResults" :key="item.id" class="history-item">
            <div class="history-meta">
              <span class="history-kind">{{ historyKindLabel(item.kind) }}</span>
              <span>{{ formatHistoryTime(item.createdAt) }}</span>
            </div>
            <div class="history-content">{{ item.preview }}</div>
          </div>
        </div>
      </section>

      <div ref="messageContainerRef" class="message-list">
        <div
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="`is-${message.role}`"
        >
          <div class="message-bubble">
            <template v-if="message.kind === 'suggestions'">
              <div class="message-text">{{ message.content }}</div>
              <div class="suggestion-list">
                <div
                  v-for="(item, index) in message.suggestions"
                  :key="`${message.id}-${index}`"
                  class="suggestion-item"
                >
                  <div class="suggestion-text">{{ item }}</div>
                  <div class="suggestion-actions">
                    <el-button size="small" text @click="copySuggestion(item)">复制</el-button>
                    <el-button size="small" text type="primary" @click="insertSuggestion(item)">
                      插入写信页
                    </el-button>
                  </div>
                </div>
              </div>
            </template>

            <template v-else-if="message.kind === 'action-items'">
              <div class="message-text">{{ message.content }}</div>
              <div class="task-list">
                <div
                  v-for="(item, index) in message.items"
                  :key="`${message.id}-${index}`"
                  class="task-item"
                >
                  <div class="task-title">
                    <span class="task-index">{{ index + 1 }}</span>
                    <span>{{ item.task }}</span>
                  </div>
                  <div class="task-meta">
                    <span><strong>截止时间：</strong>{{ item.deadline || '未指定' }}</span>
                    <span><strong>关键联系人：</strong>{{ formatContacts(item.contacts) }}</span>
                  </div>
                </div>
              </div>
            </template>

            <template v-else>
              <div class="message-text">{{ message.content }}</div>
            </template>
          </div>
        </div>

        <div v-if="loading" class="message-row is-assistant">
          <div class="message-bubble is-loading">
            <span class="typing-dot" />
            <span class="typing-dot" />
            <span class="typing-dot" />
          </div>
        </div>
      </div>

      <div class="assistant-controls">
        <el-select v-model="replyTone" size="small" class="tone-select" :disabled="loading">
          <el-option label="正式" value="formal" />
          <el-option label="简洁" value="brief" />
          <el-option label="礼貌" value="polite" />
          <el-option label="友好" value="friendly" />
        </el-select>
        <div class="quick-actions">
          <el-button :disabled="!canRun" @click="runSummary">生成摘要</el-button>
          <el-button :disabled="!canRun" @click="runActionItems">提取待办</el-button>
          <el-button :disabled="!canRun" @click="runReplySuggestions">回复建议</el-button>
        </div>
      </div>

      <div class="composer">
        <el-input
          v-model="draftPrompt"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="随意提问，或输入：总结这封邮件 / 提取待办 / 给我一版礼貌回复"
          @keyup.ctrl.enter="submitPrompt"
        />
        <div class="composer-actions">
          <span class="composer-tip">Ctrl + Enter 发送</span>
          <el-button type="primary" :disabled="!configured || loading" @click="submitPrompt">发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Close } from '@element-plus/icons-vue';
import { aiConfigApi, aiMailApi } from '@/api/ai';
import { useAiStore } from '@/stores/ai';
import { useMailStore } from '@/stores/mail';
import { MAIL_SYSTEM_NAME } from '@/constants/brand';
import { getAiHistory, getAiResultCache, pushAiHistory, setAiResultCache } from '@/utils/mailCache';

const aiStore = useAiStore();
const mailStore = useMailStore();
const router = useRouter();

const drawerVisible = computed({
  get: () => aiStore.visible,
  set: value => {
    if (!value) aiStore.close();
  }
});

const currentMail = computed(() => aiStore.currentMail);
const messageContainerRef = ref(null);
const draftPrompt = ref('');
const loading = ref(false);
const configured = ref(false);
const messages = ref([]);
const replyTone = ref('formal');
const dragActive = ref(false);
const showHistory = ref(true);

const canRun = computed(() => Boolean(currentMail.value.id) && configured.value && !loading.value);
const recentResults = computed(() => getAiHistory(currentMail.value.id));

watch(
  () => [aiStore.visible, aiStore.sessionKey],
  async ([visible]) => {
    if (!visible) return;
    await loadConfig();
    resetConversation();
    await runPendingAction();
  }
);

watch(
  messages,
  async () => {
    await nextTick();
    const element = messageContainerRef.value;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  },
  { deep: true }
);

onMounted(() => {
  if (aiStore.visible) {
    loadConfig();
    resetConversation();
  }
});

function createMessage(role, content, kind = 'text', extra = {}) {
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role,
    kind,
    content,
    ...extra
  };
}

async function loadConfig() {
  try {
    const { data } = await aiConfigApi.get();
    configured.value = Boolean(data?.configured && data?.enabled);
  } catch {
    configured.value = false;
  }
}

async function runPendingAction() {
  const action = aiStore.consumePendingAction();
  if (!action || !currentMail.value.id || !configured.value) return;
  if (action === 'summary') return runSummary(true);
  if (action === 'action-items') return runActionItems(true);
  if (action === 'reply') return runReplySuggestions(true);
}

function resetConversation() {
  const greeting = currentMail.value.id
    ? `你好！我已连接到邮件「${currentMail.value.title || '未命名邮件'}」，可以像 ChatGPT 一样自由对话，也可点击上方快捷按钮生成摘要、提取待办或获取回复建议。`
    : '你好！我是你的邮件智能助手，可以像 ChatGPT 一样自由对话。随意提问，选择邮件后还可一键生成摘要和回复建议。';
  messages.value = [createMessage('assistant', greeting)];
  draftPrompt.value = '';
}

function rememberHistory(kind, preview) {
  pushAiHistory(currentMail.value.id, { kind, preview });
}

async function runSummary(includePrompt = true) {
  if (!ensureReady()) return;
  if (includePrompt) messages.value.push(createMessage('user', '请总结这封邮件。'));
  loading.value = true;
  try {
    const cached = getAiResultCache('summary', currentMail.value.id);
    if (cached?.summary) {
      messages.value.push(createMessage('assistant', cached.summary));
      rememberHistory('summary', cached.summary);
      return;
    }
    const { data } = await aiMailApi.summarize(currentMail.value.id);
    setAiResultCache('summary', currentMail.value.id, data);
    const content = data.summary || '未返回摘要内容。';
    messages.value.push(createMessage('assistant', content));
    rememberHistory('summary', content);
  } catch (err) {
    messages.value.push(createMessage('assistant', normalizeAiError(err, '摘要')));
  } finally {
    loading.value = false;
  }
}

async function runActionItems(includePrompt = true) {
  if (!ensureReady()) return;
  if (includePrompt) messages.value.push(createMessage('user', '请提取待办、截止时间和关键联系人。'));
  loading.value = true;
  try {
    const cached = getAiResultCache('action-items', currentMail.value.id);
    if (cached?.items?.length) {
      messages.value.push(createMessage('assistant', '这是我提取到的待办事项：', 'action-items', { items: cached.items || [] }));
      rememberHistory('action-items', `提取到 ${cached.items.length} 条待办事项。`);
      return;
    }
    const { data } = await aiMailApi.extractActionItems(currentMail.value.id);
    setAiResultCache('action-items', currentMail.value.id, data);
    messages.value.push(createMessage('assistant', '这是我提取到的待办事项：', 'action-items', { items: data.items || [] }));
    rememberHistory('action-items', `提取到 ${(data.items || []).length} 条待办事项。`);
  } catch (err) {
    messages.value.push(createMessage('assistant', normalizeAiError(err, '待办提取')));
  } finally {
    loading.value = false;
  }
}

function toneLabel(value) {
  return (
    {
      formal: '正式',
      brief: '简洁',
      polite: '礼貌',
      friendly: '友好'
    }[value] || value
  );
}

async function runReplySuggestions(includePrompt = true) {
  if (!ensureReady()) return;
  if (includePrompt) messages.value.push(createMessage('user', `请给我 ${toneLabel(replyTone.value)} 风格的回复建议。`));
  loading.value = true;
  try {
    const cached = getAiResultCache('reply', currentMail.value.id, replyTone.value);
    if (cached?.suggestions?.length) {
      messages.value.push(
        createMessage('assistant', `以下是 ${toneLabel(replyTone.value)} 风格的回复建议：`, 'suggestions', {
          suggestions: cached.suggestions || []
        })
      );
      rememberHistory('reply', `${toneLabel(replyTone.value)}风格回复建议已生成。`);
      return;
    }
    const { data } = await aiMailApi.suggestReplies(currentMail.value.id, replyTone.value);
    setAiResultCache('reply', currentMail.value.id, data, replyTone.value);
    messages.value.push(
      createMessage('assistant', `以下是 ${toneLabel(replyTone.value)} 风格的回复建议：`, 'suggestions', {
        suggestions: data.suggestions || []
      })
    );
    rememberHistory('reply', `${toneLabel(replyTone.value)}风格回复建议已生成。`);
  } catch (err) {
    messages.value.push(createMessage('assistant', normalizeAiError(err, '回复建议')));
  } finally {
    loading.value = false;
  }
}

async function submitPrompt() {
  const prompt = draftPrompt.value.trim();
  if (!prompt) return;
  if (!configured.value) {
    messages.value.push(createMessage('assistant', '请先在 AI 设置中配置接入密钥和模型名称，才能使用智能助手。'));
    return;
  }
  messages.value.push(createMessage('user', prompt));
  draftPrompt.value = '';

  const normalized = prompt.toLowerCase();
  if (currentMail.value.id) {
    if (['summary', 'summarize', 'key points', '摘要', '总结', '概括'].some(kw => normalized.includes(kw) || prompt.includes(kw))) {
      return runSummary(false);
    }
    if (['todo', 'task', 'deadline', 'contact', 'action item', '待办', '任务', '截止', '联系人'].some(kw => normalized.includes(kw) || prompt.includes(kw))) {
      return runActionItems(false);
    }
    if (['reply', 'response', '回复', '回信', '回邮件'].some(kw => normalized.includes(kw) || prompt.includes(kw))) {
      updateToneByPrompt(normalized, prompt);
      return runReplySuggestions(false);
    }
  }

  loading.value = true;
  try {
    const { data } = await aiMailApi.chat(prompt, currentMail.value.id || null);
    messages.value.push(createMessage('assistant', data.reply || '未收到回复，请重试。'));
  } catch (err) {
    messages.value.push(createMessage('assistant', normalizeAiError(err, '对话')));
  } finally {
    loading.value = false;
  }
}

function updateToneByPrompt(normalizedPrompt, originalPrompt) {
  if (normalizedPrompt.includes('brief') || originalPrompt.includes('简洁')) return (replyTone.value = 'brief');
  if (normalizedPrompt.includes('polite') || originalPrompt.includes('礼貌')) return (replyTone.value = 'polite');
  if (normalizedPrompt.includes('friendly') || originalPrompt.includes('友好')) return (replyTone.value = 'friendly');
  if (normalizedPrompt.includes('formal') || originalPrompt.includes('正式')) replyTone.value = 'formal';
}

function ensureReady() {
  if (!currentMail.value.id) {
    ElMessage.warning('请先选择一封邮件。');
    return false;
  }
  if (!configured.value) {
    ElMessage.warning('请先完成 AI 设置。');
    return false;
  }
  return true;
}

function normalizeAiError(error, actionName) {
  const status = error?.response?.status;
  const message = error?.response?.data?.message || error?.message || '';
  if (status === 401) return '登录已失效，请重新登录。';
  if (status === 400 && /config|密钥|model/i.test(message)) return 'AI 配置不完整，请检查接入密钥和模型名称。';
  if (status === 502 || status === 504) return `第三方模型服务暂时不可用，无法完成${actionName}。`;
  if (/timeout/i.test(message)) return `${actionName}请求超时，请稍后重试。`;
  if (/model/i.test(message) && /not/i.test(message)) return '当前模型名称无效或暂不可用。';
  return `${actionName}失败。${message || '请稍后重试。'}`;
}

async function copySuggestion(text) {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('已复制');
  } catch {
    ElMessage.warning('复制失败');
  }
}

function insertSuggestion(text) {
  if (!currentMail.value.id) {
    ElMessage.warning('请先选择一封邮件。');
    return;
  }
  mailStore.setMailId(currentMail.value.id);
  mailStore.setMailType(currentMail.value.mailType || 'receive');
  mailStore.setPageType(currentMail.value.mailType === 'receive' ? 'reply' : 'add');
  mailStore.setAiDraftContent(text);
  aiStore.close();
  router.push('/mail_send');
}

function formatContacts(contacts) {
  return Array.isArray(contacts) && contacts.length ? contacts.join('、') : '未指定';
}

function formatHistoryTime(timestamp) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(timestamp));
}

function historyKindLabel(kind) {
  if (kind === 'summary') return '摘要';
  if (kind === 'action-items') return '待办';
  if (kind === 'reply') return '回复';
  return kind;
}

function goSettings() {
  router.push('/settings');
}

function handleClosed() {
  draftPrompt.value = '';
  dragActive.value = false;
}

function handleMailDrop(event) {
  dragActive.value = false;
  const raw = event.dataTransfer?.getData('application/x-mail-context');
  if (!raw) return;
  try {
    const mail = JSON.parse(raw);
    aiStore.openForMail(mail);
  } catch {
    ElMessage.warning('无法读取拖拽的邮件信息。');
  }
}
</script>

<style scoped lang="scss">
.ai-assistant-panel {
  height: 100%;
  width: 100%;
  padding: 16px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.drawer-shell { display: flex; flex-direction: column; height: 100%; gap: 14px; overflow: hidden; }

.assistant-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-bottom: 1px solid #f1f3f4;
  padding-bottom: 12px;
  
  .header-top-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  
  .close-panel-btn {
    color: #5f6368;
    &:hover {
      background-color: rgba(60,64,67,0.08);
    }
  }

  .header-title-area {
    h3 { margin: 4px 0 0; font-size: 18px; color: #1f1f1f; font-weight: 500; }
  }
}

.assistant-subtitle { margin: 4px 0 0; font-size: 12px; color: #5f6368; line-height: 1.4; }
.assistant-eyebrow { font-size: 11px; letter-spacing: 0.06em; color: #0b57d0; font-weight: 600; text-transform: uppercase; }
.header-actions { display: flex; align-items: center; gap: 4px; margin-top: 4px; }
.context-card { padding: 12px 14px; border: 1px solid #e0e2e6; border-radius: 12px; background: #f8fafd; transition: all 0.2s ease; &.is-drop-target { border-color: #0b57d0; box-shadow: 0 0 0 2px rgba(11,87,208,0.12); } }
.context-label { font-size: 11px; color: #5f6368; }
.context-title { margin-top: 4px; font-size: 14px; font-weight: 500; color: #1f1f1f; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.context-meta { margin-top: 4px; font-size: 12px; color: #444746; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.context-status { display: flex; gap: 8px; margin-top: 8px; }
.drop-tip { margin-top: 8px; font-size: 11px; color: #5f6368; }
.drawer-alert { margin-bottom: -2px; }
.history-card { padding: 10px 12px; border: 1px solid #e0e2e6; border-radius: 10px; background: #ffffff; }
.history-header { display: flex; align-items: center; justify-content: space-between; font-size: 12px; font-weight: 500; color: #1f1f1f; }
.history-list { display: flex; flex-direction: column; gap: 6px; margin-top: 8px; }
.history-item { padding: 8px 10px; border-radius: 8px; background: #f8fafd; border: 1px solid #f1f3f4; }
.history-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-size: 11px; color: #5f6368; }
.history-kind { color: #0b57d0; font-weight: 500; }
.history-content { margin-top: 4px; font-size: 12px; color: #444746; line-height: 1.4; }
.message-list { flex: 1; overflow-y: auto; padding-right: 4px; display: flex; flex-direction: column; gap: 12px; margin: 8px 0; }
.message-row { display: flex; &.is-user { justify-content: flex-end; } &.is-assistant, &.is-system { justify-content: flex-start; } }
.message-bubble { max-width: 90%; padding: 10px 12px; border-radius: 16px; background: #f1f3f4; color: #1f1f1f; line-height: 1.5; font-size: 13px; }
.message-row.is-user .message-bubble { background: #d3e3fd; color: #041e49; border-bottom-right-radius: 4px; }
.message-row.is-assistant .message-bubble, .message-row.is-system .message-bubble { border-bottom-left-radius: 4px; border: 1px solid #e0e2e6; background: #ffffff; }
.message-bubble.is-loading { display: inline-flex; gap: 6px; align-items: center; padding: 10px 16px; }
.typing-dot { width: 6px; height: 6px; border-radius: 50%; background: #5f6368; animation: pulse 1.2s infinite ease-in-out; }
.typing-dot:nth-child(2) { animation-delay: .15s; }
.typing-dot:nth-child(3) { animation-delay: .3s; }
.message-text { white-space: pre-wrap; word-break: break-word; }
.assistant-controls { display: flex; flex-direction: column; gap: 8px; border-top: 1px solid #f1f3f4; padding-top: 12px; }
.tone-select { width: 120px; }
.quick-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.suggestion-list, .task-list { margin-top: 8px; display: flex; flex-direction: column; gap: 8px; }
.suggestion-item, .task-item { padding: 10px; border-radius: 10px; background: #f8fafd; border: 1px solid #e0e2e6; }
.suggestion-text { color: #1f1f1f; line-height: 1.5; font-size: 12px; }
.suggestion-actions { display: flex; justify-content: flex-end; gap: 6px; margin-top: 6px; }
.task-title { display: flex; align-items: center; gap: 8px; color: #1f1f1f; font-weight: 500; font-size: 13px; }
.task-index { display: inline-flex; width: 18px; height: 18px; align-items: center; justify-content: center; border-radius: 50%; background: #0b57d0; color: #fff; font-size: 11px; }
.task-meta { display: flex; flex-direction: column; gap: 2px; margin-top: 6px; font-size: 12px; color: #444746; }
.composer { display: flex; flex-direction: column; gap: 8px; border-top: 1px solid #f1f3f4; padding-top: 12px; }
.composer-actions { display: flex; align-items: center; justify-content: space-between; }
.composer-tip { font-size: 11px; color: #5f6368; }
@keyframes pulse { 0%,80%,100% { opacity: .35; transform: translateY(0); } 40% { opacity: 1; transform: translateY(-2px); } }
</style>
