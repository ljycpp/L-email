<template>
  <el-drawer
    v-model="drawerVisible"
    direction="rtl"
    size="420px"
    class="ai-assistant-drawer"
    :with-header="false"
    @closed="handleClosed"
  >
    <div class="drawer-shell">
      <header class="assistant-header">
        <div>
          <div class="assistant-eyebrow">{{ MAIL_SYSTEM_NAME }}</div>
          <h3>鏅鸿兘鍔╂墜</h3>
          <p class="assistant-subtitle">鍙褰撳墠閭欢杩涜鎽樿銆佸緟鍔炴彁鍙栧拰鍥炲寤鸿銆?/p>
        </div>
        <div class="header-actions">
          <el-button text @click="goSettings">璁剧疆</el-button>
          <el-button text @click="resetConversation">娓呯┖</el-button>
        </div>
      </header>

      <section
        class="context-card"
        :class="{ 'is-drop-target': dragActive }"
        @dragover.prevent="dragActive = true"
        @dragleave.prevent="dragActive = false"
        @drop.prevent="handleMailDrop"
      >
        <div class="context-label">褰撳墠閭欢</div>
        <div class="context-title">{{ currentMail.title || '鏈€夋嫨閭欢' }}</div>
        <div v-if="currentMail.sender || currentMail.senderMail" class="context-meta">
          {{ currentMail.sender || currentMail.senderMail }}
          <span v-if="currentMail.senderMail && currentMail.sender !== currentMail.senderMail">
            &lt;{{ currentMail.senderMail }}&gt;
          </span>
        </div>
        <div class="context-status">
          <el-tag size="small" :type="configured ? 'success' : 'warning'">
            {{ configured ? '宸查厤缃? : '寰呴厤缃? }}
          </el-tag>
          <el-tag v-if="currentMail.id" size="small" type="info">閭欢 #{{ currentMail.id }}</el-tag>
        </div>
        <div class="drop-tip">鍙皢閭欢鍒楄〃涓殑閭欢鎷栧埌姝ゅ锛屽揩閫熷垏鎹㈠垎鏋愬璞°€?/div>
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
          <span>鏈偖浠舵渶杩戠粨鏋?/span>
          <el-button text size="small" @click="showHistory = !showHistory">
            {{ showHistory ? '鏀惰捣' : '灞曞紑' }}
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
                    <el-button size="small" text @click="copySuggestion(item)">澶嶅埗</el-button>
                    <el-button size="small" text type="primary" @click="insertSuggestion(item)">
                      鎻掑叆鍐欎俊椤?                    </el-button>
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
                    <span><strong>鎴鏃堕棿锛?/strong>{{ item.deadline || '鏈寚瀹? }}</span>
                    <span><strong>鍏抽敭鑱旂郴浜猴細</strong>{{ formatContacts(item.contacts) }}</span>
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
          <el-option label="姝ｅ紡" value="formal" />
          <el-option label="绠€娲? value="brief" />
          <el-option label="绀艰矊" value="polite" />
          <el-option label="鍙嬪ソ" value="friendly" />
        </el-select>
        <div class="quick-actions">
          <el-button :disabled="!canRun" @click="runSummary">鐢熸垚鎽樿</el-button>
          <el-button :disabled="!canRun" @click="runActionItems">鎻愬彇寰呭姙</el-button>
          <el-button :disabled="!canRun" @click="runReplySuggestions">鍥炲寤鸿</el-button>
        </div>
      </div>

      <div class="composer">
        <el-input
          v-model="draftPrompt"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="渚嬪锛氭€荤粨杩欏皝閭欢 / 鎻愬彇寰呭姙 / 缁欐垜涓€鐗堢ぜ璨屽洖澶?
          @keyup.ctrl.enter="submitPrompt"
        />
        <div class="composer-actions">
          <span class="composer-tip">Ctrl + Enter 鍙戦€?/span>
          <el-button type="primary" :disabled="!currentMail.id || loading" @click="submitPrompt">鍙戦€?/el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
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
    if (element) element.scrollTop = element.scrollHeight;
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
  messages.value = [
    createMessage(
      'assistant',
      currentMail.value.id
        ? `宸茶繛鎺ュ埌鈥?{currentMail.value.title || '鏈懡鍚嶉偖浠?}鈥濄€傛垜鍙互涓轰綘鐢熸垚鎽樿銆佹彁鍙栧緟鍔炴垨鎻愪緵鍥炲寤鸿銆俙
        : '璇峰厛閫夋嫨涓€灏侀偖浠讹紝鍐嶆墦寮€鏅鸿兘鍔╂墜銆?
    )
  ];
  draftPrompt.value = '';
}

function rememberHistory(kind, preview) {
  pushAiHistory(currentMail.value.id, { kind, preview });
}

async function runSummary(includePrompt = true) {
  if (!ensureReady()) return;
  if (includePrompt) messages.value.push(createMessage('user', '璇锋€荤粨杩欏皝閭欢銆?));
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
    const content = data.summary || '鏈繑鍥炴憳瑕佸唴瀹广€?;
    messages.value.push(createMessage('assistant', content));
    rememberHistory('summary', content);
  } catch (err) {
    messages.value.push(createMessage('assistant', normalizeAiError(err, '鎽樿')));
  } finally {
    loading.value = false;
  }
}

async function runActionItems(includePrompt = true) {
  if (!ensureReady()) return;
  if (includePrompt) messages.value.push(createMessage('user', '璇锋彁鍙栧緟鍔炪€佹埅姝㈡椂闂村拰鍏抽敭鑱旂郴浜恒€?));
  loading.value = true;
  try {
    const cached = getAiResultCache('action-items', currentMail.value.id);
    if (cached?.items?.length) {
      messages.value.push(createMessage('assistant', '杩欐槸鎴戞彁鍙栧埌鐨勫緟鍔炰簨椤癸細', 'action-items', { items: cached.items || [] }));
      rememberHistory('action-items', `鎻愬彇鍒?${cached.items.length} 鏉″緟鍔炰簨椤广€俙);
      return;
    }
    const { data } = await aiMailApi.extractActionItems(currentMail.value.id);
    setAiResultCache('action-items', currentMail.value.id, data);
    messages.value.push(createMessage('assistant', '杩欐槸鎴戞彁鍙栧埌鐨勫緟鍔炰簨椤癸細', 'action-items', { items: data.items || [] }));
    rememberHistory('action-items', `鎻愬彇鍒?${(data.items || []).length} 鏉″緟鍔炰簨椤广€俙);
  } catch (err) {
    messages.value.push(createMessage('assistant', normalizeAiError(err, '寰呭姙鎻愬彇')));
  } finally {
    loading.value = false;
  }
}

async function runReplySuggestions(includePrompt = true) {
  if (!ensureReady()) return;
  if (includePrompt) messages.value.push(createMessage('user', `璇风粰鎴?${replyTone.value} 椋庢牸鐨勫洖澶嶅缓璁€俙));
  loading.value = true;
  try {
    const cached = getAiResultCache('reply', currentMail.value.id, replyTone.value);
    if (cached?.suggestions?.length) {
      messages.value.push(createMessage('assistant', `浠ヤ笅鏄?${replyTone.value} 椋庢牸鐨勫洖澶嶅缓璁細`, 'suggestions', { suggestions: cached.suggestions || [] }));
      rememberHistory('reply', `${replyTone.value} 椋庢牸鍥炲寤鸿宸茬敓鎴愩€俙);
      return;
    }
    const { data } = await aiMailApi.suggestReplies(currentMail.value.id, replyTone.value);
    setAiResultCache('reply', currentMail.value.id, data, replyTone.value);
    messages.value.push(createMessage('assistant', `浠ヤ笅鏄?${replyTone.value} 椋庢牸鐨勫洖澶嶅缓璁細`, 'suggestions', { suggestions: data.suggestions || [] }));
    rememberHistory('reply', `${replyTone.value} 椋庢牸鍥炲寤鸿宸茬敓鎴愩€俙);
  } catch (err) {
    messages.value.push(createMessage('assistant', normalizeAiError(err, '鍥炲寤鸿')));
  } finally {
    loading.value = false;
  }
}

async function submitPrompt() {
  const prompt = draftPrompt.value.trim();
  if (!prompt) return;
  messages.value.push(createMessage('user', prompt));
  draftPrompt.value = '';

  if (!currentMail.value.id) {
    messages.value.push(createMessage('assistant', '璇峰厛閫夋嫨涓€灏侀偖浠躲€?));
    return;
  }
  if (!configured.value) {
    messages.value.push(createMessage('assistant', '请先在 AI 设置中配置接入密钥和模型名称。'));
    return;
  }

  const normalized = prompt.toLowerCase();
  if (['summary', 'summarize', 'key points', '鎽樿', '鎬荤粨'].some(item => normalized.includes(item) || prompt.includes(item))) {
    return runSummary(false);
  }
  if (['todo', 'task', 'deadline', 'contact', 'action item', '寰呭姙', '浠诲姟', '鎴', '鑱旂郴浜?].some(item => normalized.includes(item) || prompt.includes(item))) {
    return runActionItems(false);
  }
  if (['reply', 'response', '鍥炲', '鍥炰俊'].some(item => normalized.includes(item) || prompt.includes(item))) {
    updateToneByPrompt(normalized, prompt);
    return runReplySuggestions(false);
  }

  messages.value.push(createMessage('assistant', '褰撳墠鏀寔锛氶偖浠舵憳瑕併€佸緟鍔炴彁鍙栥€佸洖澶嶅缓璁€?));
}

function updateToneByPrompt(normalizedPrompt, originalPrompt) {
  if (normalizedPrompt.includes('brief') || originalPrompt.includes('绠€娲?)) return (replyTone.value = 'brief');
  if (normalizedPrompt.includes('polite') || originalPrompt.includes('绀艰矊')) return (replyTone.value = 'polite');
  if (normalizedPrompt.includes('friendly') || originalPrompt.includes('鍙嬪ソ')) return (replyTone.value = 'friendly');
  if (normalizedPrompt.includes('formal') || originalPrompt.includes('姝ｅ紡')) replyTone.value = 'formal';
}

function ensureReady() {
  if (!currentMail.value.id) {
    ElMessage.warning('璇峰厛閫夋嫨涓€灏侀偖浠躲€?);
    return false;
  }
  if (!configured.value) {
    ElMessage.warning('璇峰厛瀹屾垚 AI 璁剧疆銆?);
    return false;
  }
  return true;
}

function normalizeAiError(error, actionName) {
  const status = error?.response?.status;
  const message = error?.response?.data?.message || error?.message || '';
  if (status === 401) return '鐧诲綍宸插け鏁堬紝璇烽噸鏂扮櫥褰曘€?;
  if (status === 400 && /config|密钥|model/i.test(message)) return 'AI 配置不完整，请检查接入密钥和模型名称。';
  if (status === 502 || status === 504) return `绗笁鏂规ā鍨嬫湇鍔℃殏鏃朵笉鍙敤锛屾棤娉曞畬鎴?{actionName}銆俙;
  if (/timeout/i.test(message)) return `${actionName}璇锋眰瓒呮椂锛岃绋嶅悗閲嶈瘯銆俙;
  if (/model/i.test(message) && /not/i.test(message)) return '褰撳墠妯″瀷鍚嶇О鏃犳晥鎴栨殏涓嶅彲鐢ㄣ€?;
  return `${actionName}澶辫触銆?{message || '璇风◢鍚庨噸璇曘€?}`;
}

async function copySuggestion(text) {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('宸插鍒?);
  } catch {
    ElMessage.warning('澶嶅埗澶辫触');
  }
}

function insertSuggestion(text) {
  if (!currentMail.value.id) {
    ElMessage.warning('璇峰厛閫夋嫨涓€灏侀偖浠躲€?);
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
  return Array.isArray(contacts) && contacts.length ? contacts.join('锛?) : '鏈寚瀹?;
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
  if (kind === 'summary') return '鎽樿';
  if (kind === 'action-items') return '寰呭姙';
  if (kind === 'reply') return '鍥炲';
  return kind;
}

function goSettings() {
  router.push('/ai-settings');
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
    ElMessage.warning('鏃犳硶璇诲彇鎷栨嫿鐨勯偖浠朵俊鎭€?);
  }
}
</script>

<style scoped lang="scss">
.drawer-shell { display: flex; flex-direction: column; height: 100%; gap: 14px; }
.assistant-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; h3 { margin: 4px 0 0; font-size: 20px; color: #303133; } }
.assistant-subtitle { margin: 6px 0 0; font-size: 13px; color: #7a8596; }
.assistant-eyebrow { font-size: 12px; letter-spacing: 0.06em; color: #1a73e8; font-weight: 600; }
.header-actions { display: flex; align-items: center; gap: 4px; }
.context-card { padding: 14px 16px; border: 1px solid #e8eef7; border-radius: 16px; background: linear-gradient(145deg, #f8fbff 0%, #eef5ff 100%); transition: all 0.2s ease; &.is-drop-target { border-color: #1a73e8; box-shadow: 0 0 0 2px rgba(26,115,232,0.12); } }
.context-label { font-size: 12px; color: #909399; }
.context-title { margin-top: 6px; font-size: 16px; font-weight: 600; color: #303133; line-height: 1.4; }
.context-meta { margin-top: 6px; font-size: 13px; color: #606266; }
.context-status { display: flex; gap: 8px; margin-top: 12px; }
.drop-tip { margin-top: 10px; font-size: 12px; color: #7a8596; }
.drawer-alert { margin-bottom: -2px; }
.history-card { padding: 12px 14px; border: 1px solid #edf0f5; border-radius: 14px; background: #fcfdff; }
.history-header { display: flex; align-items: center; justify-content: space-between; font-size: 13px; font-weight: 600; color: #303133; }
.history-list { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.history-item { padding: 10px 12px; border-radius: 12px; background: #fff; border: 1px solid #eef2f7; }
.history-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-size: 12px; color: #909399; }
.history-kind { color: #1a73e8; font-weight: 600; }
.history-content { margin-top: 4px; font-size: 13px; color: #606266; line-height: 1.5; }
.message-list { flex: 1; overflow-y: auto; padding-right: 4px; display: flex; flex-direction: column; gap: 12px; }
.message-row { display: flex; &.is-user { justify-content: flex-end; } &.is-assistant, &.is-system { justify-content: flex-start; } }
.message-bubble { max-width: 88%; padding: 12px 14px; border-radius: 18px; background: #f5f7fa; color: #303133; line-height: 1.6; box-shadow: 0 10px 24px rgba(31,45,61,0.06); }
.message-row.is-user .message-bubble { background: linear-gradient(135deg, #1a73e8 0%, #4f8ff7 100%); color: #fff; border-bottom-right-radius: 8px; }
.message-row.is-assistant .message-bubble, .message-row.is-system .message-bubble { border-bottom-left-radius: 8px; }
.message-bubble.is-loading { display: inline-flex; gap: 6px; align-items: center; }
.typing-dot { width: 8px; height: 8px; border-radius: 50%; background: #7f8ea3; animation: pulse 1.2s infinite ease-in-out; }
.typing-dot:nth-child(2) { animation-delay: .15s; }
.typing-dot:nth-child(3) { animation-delay: .3s; }
.message-text { white-space: pre-wrap; word-break: break-word; }
.assistant-controls { display: flex; flex-direction: column; gap: 10px; }
.tone-select { width: 160px; }
.quick-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.suggestion-list, .task-list { margin-top: 10px; display: flex; flex-direction: column; gap: 10px; }
.suggestion-item, .task-item { padding: 12px; border-radius: 14px; background: rgba(255,255,255,.9); border: 1px solid #e8eef7; }
.suggestion-text { color: #303133; line-height: 1.6; }
.suggestion-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 6px; }
.task-title { display: flex; align-items: center; gap: 10px; color: #303133; font-weight: 600; }
.task-index { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; border-radius: 50%; background: #1a73e8; color: #fff; font-size: 12px; }
.task-meta { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; font-size: 13px; color: #606266; }
.composer { display: flex; flex-direction: column; gap: 10px; }
.composer-actions { display: flex; align-items: center; justify-content: space-between; }
.composer-tip { font-size: 12px; color: #909399; }
@keyframes pulse { 0%,80%,100% { opacity: .35; transform: translateY(0); } 40% { opacity: 1; transform: translateY(-2px); } }
</style>

