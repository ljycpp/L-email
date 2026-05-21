<template>
  <div v-loading="loading" class="compose-page">
    <div
      class="compose-card"
      :class="{ 'is-dragover': dragOver }"
      @dragover.prevent="dragOver = true"
      @dragleave.prevent="dragOver = false"
      @drop.prevent="onDropFiles"
    >
      <div v-if="composeModeLabel" class="compose-mode">{{ composeModeLabel }}</div>
      <div class="compose-actions">
        <el-button type="primary" size="default" :loading="sending" @click="send">发送</el-button>
        <el-button text :disabled="sending" @click="saveDraft">保存草稿</el-button>
        <el-button text :disabled="sending" @click="triggerUpload">
          <el-icon><Paperclip /></el-icon>
          添加附件
        </el-button>
        <span class="attachment-hint">单个文件不超过 20MB，禁止上传可执行文件，也可直接拖拽到本区域。</span>
        <input ref="fileInputRef" type="file" multiple :accept="acceptedFileTypes" class="hidden-file-input" @change="onFileInputChange" />
      </div>

      <el-alert v-if="lastUploadError" type="warning" :closable="false" :title="lastUploadError" class="upload-alert" />

      <div class="compose-row compose-row-tools">
        <label class="row-label">快捷添加</label>
        <el-select
          v-model="selectedGroupId"
          class="group-picker"
          placeholder="从分组中添加收件人"
          clearable
          filterable
          @change="addFromGroup"
        >
          <el-option v-for="group in groupOptions" :key="group.id" :label="group.name" :value="group.id" />
        </el-select>
      </div>

      <div class="compose-row">
        <label class="row-label">收件人</label>
        <el-select v-model="target" class="row-control" multiple filterable allow-create default-first-option placeholder="输入邮箱并按回车添加" value-key="mail">
          <el-option v-for="contact in contactOptions" :key="contact.mail" :label="contact.show" :value="contact" />
        </el-select>
        <button type="button" class="row-link" @click="showCc = !showCc">{{ showCc ? '隐藏抄送' : '抄送' }}</button>
      </div>

      <div v-show="showCc" class="compose-row">
        <label class="row-label">抄送</label>
        <el-select v-model="copy" class="row-control" multiple filterable allow-create default-first-option placeholder="添加抄送人" value-key="mail">
          <el-option v-for="contact in contactOptions" :key="`cc-${contact.mail}`" :label="contact.show" :value="contact" />
        </el-select>
      </div>

      <div class="compose-row">
        <label class="row-label">主题</label>
        <el-input v-model="mail.title" class="row-control" placeholder="请输入邮件主题" clearable />
      </div>

      <div v-if="fileList.length" class="compose-attachments">
        <span class="attach-label">附件</span>
        <div class="attach-panel">
          <div class="attach-summary">已添加 {{ fileList.length }} 个附件，总大小 {{ formatFileSize(totalAttachmentSize) }}</div>
          <div class="attach-list">
            <div v-for="file in fileList" :key="file.uid" class="attach-item">
              <el-icon><Document /></el-icon>
              <a v-if="file.url" :href="buildAttachmentHref(file)" target="_blank" rel="noopener noreferrer" class="attach-name attach-link" @click.stop>{{ file.name }}</a>
              <span v-else class="attach-name">{{ file.name }}</span>
              <span class="attach-size">{{ formatFileSize(file.size || 0) }}</span>
              <el-icon class="attach-remove" @click="removeFile(file)"><Close /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <div class="compose-editor">
        <QuillEditor v-model:content="mail.content" content-type="html" theme="snow" :options="editorOptions" />
      </div>

      <div class="compose-footer">发件人：{{ userStore.name || '当前用户' }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Close, Document, Paperclip } from '@element-plus/icons-vue';
import { QuillEditor } from '@vueup/vue-quill';
import request from '@/utils/request';
import { contactApi, groupApi, mailDetailApi, mailSendApi } from '@/api/mail';
import { useMailStore } from '@/stores/mail';
import { useUserStore } from '@/stores/user';
import { formatFileSize, formatTime, isEmail } from '@/utils/format';
import { resolveApiUrl } from '@/utils/url';
import { buildAttachmentHref } from '@/utils/attachment';

const MAX_FILE_SIZE = 20 * 1024 * 1024;
const BLOCKED_EXTENSIONS = ['exe', 'bat', 'cmd', 'sh', 'msi', 'jar', 'com', 'scr'];
const ACCEPTED_EXTENSIONS = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx', '.txt', '.csv', '.png', '.jpg', '.jpeg', '.gif', '.zip', '.rar'];

const router = useRouter();
const mailStore = useMailStore();
const userStore = useUserStore();
const loading = ref(false);
const sending = ref(false);
const showCc = ref(false);
const fileInputRef = ref(null);
const target = ref([]);
const copy = ref([]);
const fileList = ref([]);
const attachmentIds = ref([]);
const contactOptions = ref([]);
const groupOptions = ref([]);
const selectedGroupId = ref(null);
const dragOver = ref(false);
const lastUploadError = ref('');

const acceptedFileTypes = ACCEPTED_EXTENSIONS.join(',');
const totalAttachmentSize = computed(() => fileList.value.reduce((sum, file) => sum + (file.size || 0), 0));
const composeModeLabel = computed(() => ({ reply: '回复邮件', replyAll: '回复全部', forward: '转发邮件', edit: '编辑草稿' }[mailStore.pageType] || ''));
const mail = reactive({ title: '', content: '' });

const editorOptions = {
  placeholder: '在这里输入邮件正文',
  modules: {
    toolbar: [['bold', 'italic', 'underline', 'strike'], [{ color: [] }, { background: [] }], [{ header: [1, 2, 3, false] }], [{ align: [] }], [{ list: 'ordered' }, { list: 'bullet' }], ['blockquote', 'code-block'], ['link'], ['clean']]
  }
};

onMounted(() => initPage());

async function initPage() {
  loading.value = true;
  try {
    await Promise.all([loadContacts(), loadGroups()]);
    await loadDraftOrReply();
    applyAiDraft();
    if (mailStore.target) {
      target.value = dedupeRecipients(mailStore.target);
      mailStore.setTarget(null);
    }
  } finally {
    loading.value = false;
  }
}

async function loadContacts() {
  const { data } = await contactApi.list({ page: 1, limit: 500 });
  contactOptions.value = (data.contacts || []).map(item => ({ ...item, show: `${item.name}<${item.mail}>` }));
}

async function loadGroups() {
  const { data } = await groupApi.list();
  groupOptions.value = data.groupList || [];
}

async function addFromGroup(groupId) {
  if (!groupId) return;
  selectedGroupId.value = null;
  const { data } = await groupApi.contacts(groupId);
  const contacts = data.contacts || [];
  if (!contacts.length) return ElMessage.warning('该分组暂无联系人。');
  const existing = new Set(target.value.map(item => item.mail));
  let added = 0;
  for (const contact of contacts) {
    if (!existing.has(contact.mail)) {
      target.value.push({ ...contact, show: `${contact.name}<${contact.mail}>` });
      existing.add(contact.mail);
      added += 1;
    }
  }
  ElMessage.success(added ? `已添加 ${added} 位收件人。` : '收件人已存在。');
}

async function loadDraftOrReply() {
  const pageType = mailStore.pageType;
  const mailId = mailStore.mailId || mailStore.draftId;
  if (!pageType || pageType === 'add' || !mailId) return;
  const mailType = mailStore.mailType || 'receive';
  const { data } = await mailDetailApi.get({ mailId, mailType });
  const originalTargets = mapParty(data.target);
  const originalCopies = mapParty(data.copy);
  mail.title = data.title || '';
  mail.content = data.content || '';
  fileList.value = [];
  attachmentIds.value = [];

  if (pageType === 'edit') {
    target.value = originalTargets;
    copy.value = originalCopies;
    showCc.value = copy.value.length > 0;
    fileList.value = (data.oldFileList || []).map((file, index) => ({ name: file.name, url: resolveApiUrl(file.url), uid: `old-${index}`, id: file.id, size: file.size || 0 }));
    attachmentIds.value = (data.oldFileList || []).map(file => file.id).filter(Boolean);
    return;
  }

  if (pageType === 'reply') {
    mail.title = withPrefix(mail.title, 'Re: ');
    target.value = [{ name: data.sender, mail: data.sendMail, show: `${data.sender}<${data.sendMail}>` }];
    mail.content = buildQuotedContent(data);
    return;
  }

  if (pageType === 'replyAll') {
    mail.title = withPrefix(mail.title, 'Re: ');
    const recipients = [{ name: data.sender, mail: data.sendMail, show: `${data.sender}<${data.sendMail}>` }, ...originalTargets, ...originalCopies].filter(item => item.mail && item.mail !== userStore.email);
    target.value = dedupeRecipients(recipients);
    copy.value = [];
    showCc.value = false;
    mail.content = buildQuotedContent(data);
    return;
  }

  if (pageType === 'forward') {
    mail.title = withPrefix(mail.title, 'Fwd: ');
    target.value = [];
    copy.value = [];
    showCc.value = false;
    mail.content = buildForwardContent(data);
    fileList.value = (data.oldFileList || []).map((file, index) => ({ name: file.name, url: resolveApiUrl(file.url), uid: `old-${index}`, id: file.id, size: file.size || 0 }));
    attachmentIds.value = (data.oldFileList || []).map(file => file.id).filter(Boolean);
  }
}

function buildQuotedContent(data) {
  const time = formatTime(data.receiveDate || data.sendDate);
  return `<p><br></p><p style="color:#909399">在 ${time}，${data.sender} &lt;${data.sendMail}&gt; 写道：</p>${data.content || ''}`;
}

function buildForwardContent(data) {
  const time = formatTime(data.receiveDate || data.sendDate);
  const toLine = (data.target || []).map(p => `${p.name} &lt;${p.mail}&gt;`).join('；');
  const ccLine = (data.copy || []).map(p => `${p.name} &lt;${p.mail}&gt;`).join('；');
  const header = ['<p><br></p>', '<p><b>---------- 转发的邮件 ----------</b></p>', `<p><b>发件人：</b>${data.sender} &lt;${data.sendMail}&gt;</p>`, `<p><b>时间：</b>${time}</p>`, `<p><b>收件人：</b>${toLine || '-'}</p>`, ccLine ? `<p><b>抄送：</b>${ccLine}</p>` : '', `<p><b>主题：</b>${data.title || ''}</p>`, '<p><br></p>'].join('');
  return `${header}${data.content || ''}`;
}

function withPrefix(title, prefix) {
  return title?.startsWith(prefix) ? title : `${prefix}${title || ''}`;
}
function mapParty(list = []) { return list.map(person => ({ ...person, show: `${person.name}<${person.mail}>` })); }
function dedupeRecipients(list = []) {
  const seen = new Set();
  const output = [];
  for (const item of list) {
    const email = typeof item === 'string' ? item : item.mail;
    if (!email || seen.has(email)) continue;
    seen.add(email);
    output.push(item);
  }
  return output;
}

function applyAiDraft() {
  if (!mailStore.aiDraftContent) return;
  const suggestionHtml = toParagraphHtml(mailStore.aiDraftContent);
  mail.content = mail.content ? `${suggestionHtml}<p><br></p>${mail.content}` : suggestionHtml;
  mailStore.setAiDraftContent('');
}

function toParagraphHtml(text) {
  const safe = String(text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  return `<p>${safe.replace(/\n/g, '<br>')}</p>`;
}

function triggerUpload() { fileInputRef.value?.click(); }
async function onDropFiles(event) { dragOver.value = false; await uploadFiles(Array.from(event.dataTransfer?.files || [])); }
async function onFileInputChange(event) { const files = Array.from(event.target.files || []); event.target.value = ''; await uploadFiles(files); }

async function uploadFiles(files) {
  lastUploadError.value = '';
  for (const file of files) {
    const validation = validateFile(file);
    if (validation) {
      lastUploadError.value = validation;
      ElMessage.warning(validation);
      continue;
    }
    try {
      await uploadFile({ file, onSuccess: () => {} });
      lastUploadError.value = '';
    } catch (error) {
      const message = error?.response?.data?.message || '附件上传失败';
      lastUploadError.value = message;
      ElMessage.error(message);
    }
  }
}

function validateFile(file) {
  const extension = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : '';
  if (BLOCKED_EXTENSIONS.includes(extension)) return `出于安全原因，禁止上传 .${extension} 文件。`;
  if (file.size > MAX_FILE_SIZE) return `${file.name} 超过 20MB 限制。`;
  return '';
}

async function uploadFile(options) {
  const formData = new FormData();
  formData.append('file', options.file);
  const { data } = await request.post('/api/attachments/upload', formData);
  attachmentIds.value.push(data.id);
  fileList.value.push({ name: data.name, url: resolveApiUrl(data.url), id: data.id, uid: `${data.id}-${Date.now()}`, size: data.size || options.file.size });
  options.onSuccess?.(data);
}

function removeFile(file) {
  attachmentIds.value = attachmentIds.value.filter(id => id !== file.id);
  fileList.value = fileList.value.filter(item => item.uid !== file.uid);
}

function buildFormData(includeDraftId) {
  const form = new FormData();
  form.append('title', mail.title || '');
  form.append('content', mail.content || '');
  if (includeDraftId && mailStore.mailType === 'draft' && mailStore.draftId) form.append('draftId', mailStore.draftId);
  normalizeRecipients(target.value).forEach((email, index) => form.append(`target[${index}]`, email));
  normalizeRecipients(copy.value).forEach((email, index) => form.append(`copy[${index}]`, email));
  attachmentIds.value.forEach((id, index) => form.append(`attachmentIds[${index}]`, id));
  return form;
}

function normalizeRecipients(list) {
  return list.map(item => {
    if (typeof item === 'string') {
      if (!isEmail(item)) throw new Error('invalid');
      return item;
    }
    if (!isEmail(item.mail)) throw new Error('invalid');
    return item.mail;
  });
}

async function send() {
  if (!target.value.length) return ElMessage.warning('请至少添加一位收件人。');
  try {
    normalizeRecipients(target.value);
    normalizeRecipients(copy.value);
  } catch {
    return ElMessage.warning('收件人列表中存在无效邮箱。');
  }
  sending.value = true;
  try {
    await mailSendApi.send(buildFormData(false));
    ElMessage.success('邮件已发送。');
    mailStore.clearComposeContext();
    router.push('/outbox');
  } finally {
    sending.value = false;
  }
}

async function saveDraft() {
  sending.value = true;
  try {
    await mailSendApi.draft(buildFormData(true));
    ElMessage.success('草稿已保存。');
    mailStore.clearComposeContext();
    router.push('/draftbox');
  } finally {
    sending.value = false;
  }
}
</script>

<style scoped lang="scss">
.compose-page { padding: 0; max-width: 100%; }
.compose-card { background: #fff; border: 1px solid #e4e7ed; border-radius: 4px; overflow: hidden; box-shadow: 0 1px 4px rgba(0,0,0,.04); transition: border-color .2s, box-shadow .2s; &.is-dragover { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64,158,255,.15); } }
.compose-mode { padding: 8px 16px; background: #ecf5ff; color: #409eff; font-size: 13px; font-weight: 600; border-bottom: 1px solid #d9ecff; }
.compose-actions { display: flex; align-items: center; gap: 4px; padding: 12px 16px; border-bottom: 1px solid #f0f0f0; background: #fafafa; flex-wrap: wrap; .el-button--primary { min-width: 72px; background: #1a73e8; border-color: #1a73e8; &:hover { background: #1557b0; border-color: #1557b0; } } .el-button.is-text { color: #606266; &:hover { color: #1a73e8; background: rgba(26,115,232,.06); } } }
.attachment-hint { font-size: 12px; color: #909399; margin-left: auto; }
.upload-alert { margin: 12px 16px 0; }
.hidden-file-input { display: none; }
.compose-row { display: flex; align-items: center; min-height: 44px; padding: 0 16px; border-bottom: 1px solid #f0f0f0; .row-label { flex-shrink: 0; width: 72px; font-size: 14px; color: #909399; line-height: 44px; } .row-control { flex: 1; min-width: 0; } .row-link { flex-shrink: 0; margin-left: 12px; padding: 0; border: none; background: none; font-size: 13px; color: #1a73e8; cursor: pointer; line-height: 44px; &:hover { text-decoration: underline; } } :deep(.el-select) .el-select__wrapper { box-shadow: none !important; border: none; padding-left: 0; background: transparent; } :deep(.el-input__wrapper) { box-shadow: none !important; border: none; padding-left: 0; background: transparent; } }
.group-picker { max-width: 320px; }
.compose-attachments { display: flex; align-items: flex-start; padding: 10px 16px; border-bottom: 1px solid #f0f0f0; background: #fafcff; .attach-label { flex-shrink: 0; width: 72px; font-size: 14px; color: #909399; line-height: 28px; } .attach-panel { flex: 1; display: flex; flex-direction: column; gap: 8px; } .attach-summary { font-size: 12px; color: #909399; } .attach-list { flex: 1; display: flex; flex-wrap: wrap; gap: 8px; } .attach-item { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; background: #fff; border: 1px solid #dce6f5; border-radius: 4px; font-size: 13px; color: #303133; } .attach-name { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } .attach-size { color: #909399; font-size: 12px; } .attach-remove { cursor: pointer; color: #909399; &:hover { color: #f56c6c; } } }
.compose-editor { :deep(.quill-editor) { display: flex; flex-direction: column; } :deep(.ql-toolbar.ql-snow) { order: 0; border: none; border-bottom: 1px solid #f0f0f0; background: #fafafa; padding: 8px 12px; font-family: inherit; } :deep(.ql-container.ql-snow) { order: 1; border: none; font-size: 14px; } :deep(.ql-editor) { min-height: 380px; padding: 20px 24px; line-height: 1.7; color: #303133; &.ql-blank::before { color: #c0c4cc; font-style: normal; left: 24px; } } }
.compose-footer { padding: 10px 16px; font-size: 12px; color: #909399; border-top: 1px solid #f0f0f0; background: #fafafa; }
</style>
