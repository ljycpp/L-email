<template>
  <div class="app-container ai-settings-page">
    <div class="settings-title">
      <h2>设置</h2>
      <p>管理账号展示、界面偏好和智能能力。</p>
    </div>
    <el-row class="settings-grid" :gutter="20">
      <el-col :lg="14" :md="16" :sm="24">
        <el-card shadow="never" class="settings-card">
          <template #header>
            <div class="card-header">
              <div>
                <div class="card-eyebrow">通用设置</div>
                <h3>基本设置</h3>
              </div>
            </div>
          </template>

          <div class="basic-settings">
            <div class="basic-row">
              <span class="basic-label">用户名</span>
              <div class="profile-edit">
                <el-input v-model="profileForm.nickname" maxlength="30" show-word-limit placeholder="请输入用户名" />
                <el-button class="soft-btn primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
              </div>
            </div>
            <div class="basic-row">
              <span class="basic-label">登录邮箱</span>
              <span class="basic-value">{{ userStore.email || '-' }}</span>
            </div>
            <div class="basic-row">
              <span class="basic-label">折叠侧边栏</span>
              <div class="switch-control">
                <el-switch v-model="appStore.sidebarCollapsed" />
                <span>{{ appStore.sidebarCollapsed ? '已折叠' : '已展开' }}</span>
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="settings-card">
          <template #header>
            <div class="card-header">
              <div>
                <div class="card-eyebrow">智能能力</div>
                <h2>AI 接入设置</h2>
              </div>
              <el-tag class="soft-tag">VVEAI</el-tag>
            </div>
          </template>

          <el-form label-position="top" :model="form" class="settings-form">
            <el-form-item label="接口地址">
              <el-input model-value="https://api.vveai.com/v1/chat/completions" disabled />
            </el-form-item>

            <el-form-item label="接入密钥">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                placeholder="填写第三方模型接入密钥；留空则保留已保存的密钥"
              />
              <div v-if="config.configured" class="field-tip">
                当前已保存接入信息，如需替换可直接填写新的密钥。
              </div>
            </el-form-item>

            <el-form-item label="模型名称">
              <el-input v-model="form.modelName" placeholder="例如：gpt-4o-mini" />
            </el-form-item>

            <el-form-item label="启用智能助手">
              <div class="switch-control">
                <el-switch v-model="form.enabled" />
                <span>{{ form.enabled ? '已开启' : '已关闭' }}</span>
              </div>
            </el-form-item>

            <div class="actions">
              <el-button class="soft-btn secondary" :loading="testing" @click="testConfig">测试连接</el-button>
              <el-button class="soft-btn primary" :loading="saving" @click="saveConfig">保存设置</el-button>
            </div>
          </el-form>
        </el-card>

        <el-card shadow="never" class="settings-card">
          <template #header>
            <div class="card-header">
              <div>
                <div class="card-eyebrow">自动化能力</div>
                <h3>自动处理设置</h3>
              </div>
            </div>
          </template>

          <div class="feature-item">
            <div class="feature-info">
              <div class="feature-title">垃圾邮件自动过滤</div>
              <div class="feature-desc">开启后，新收到的邮件会自动交给系统内置垃圾邮件模型检测。识别为垃圾邮件时，会自动移入垃圾邮件箱。</div>
            </div>
            <div class="feature-control">
              <SpamFilterSwitch ref="spamSwitchRef" />
            </div>
          </div>

          <div class="feature-item">
            <div class="feature-info">
              <div class="feature-title">自动优先级判断</div>
              <div class="feature-desc">开启后，新收到的邮件会自动评分，并按高、中、低优先级分类，高优先级邮件会置顶显示。（依赖上方 AI 配置）</div>
            </div>
            <div class="feature-control">
              <PriorityFilterSwitch ref="prioritySwitchRef" />
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :lg="10" :md="8" :sm="24">
        <el-card shadow="never" class="guide-card">
          <template #header>
            <div class="card-header">
              <div>
                <div class="card-eyebrow">使用说明</div>
                <h3>助手能力</h3>
              </div>
            </div>
          </template>

          <ul class="guide-list">
            <li>使用您自己的第三方模型接入密钥和模型名称，由后端加密保存。</li>
            <li>仅根据当前选中的邮件生成摘要、待办提取和回复建议。</li>
            <li>兼容 VVEAI 的 Chat Completions 接口。</li>
            <li>AI 调用失败不会影响收发信等核心功能。</li>
          </ul>

          <el-alert
            v-if="config.lastTestStatus"
            :type="config.lastTestStatus === 'SUCCESS' ? 'success' : 'error'"
            :title="config.lastTestStatus === 'SUCCESS' ? '最近一次连接测试成功' : '最近一次连接测试失败'"
            :description="config.lastTestMessage || ''"
            :closable="false"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { aiConfigApi } from '@/api/ai';
import SpamFilterSwitch from '@/components/SpamFilterSwitch.vue';
import PriorityFilterSwitch from '@/components/PriorityFilterSwitch.vue';
import { useAppStore } from '@/stores/app';
import { useUserStore } from '@/stores/user';

const saving = ref(false);
const testing = ref(false);
const profileSaving = ref(false);
const spamSwitchRef = ref(null);
const prioritySwitchRef = ref(null);
const appStore = useAppStore();
const userStore = useUserStore();

const config = reactive({
  apiKeyMasked: '',
  modelName: '',
  enabled: false,
  configured: false,
  lastTestStatus: '',
  lastTestMessage: ''
});

const form = reactive({
  apiKey: '',
  modelName: '',
  enabled: false
});

const profileForm = reactive({
  nickname: ''
});

onMounted(loadConfig);

async function loadConfig() {
  profileForm.nickname = userStore.name || '';
  const { data } = await aiConfigApi.get();
  Object.assign(config, data || {});
  form.apiKey = '';
  form.modelName = data?.modelName || '';
  form.enabled = Boolean(data?.enabled);
}

async function saveProfile() {
  const nickname = profileForm.nickname.trim();
  if (!nickname) {
    ElMessage.warning('请先输入用户名');
    return;
  }
  profileSaving.value = true;
  try {
    await userStore.updateProfile({ nickname });
    profileForm.nickname = userStore.name || nickname;
    ElMessage.success('用户名已更新');
  } finally {
    profileSaving.value = false;
  }
}

async function testConfig() {
  if (!form.modelName.trim()) {
    ElMessage.warning('请填写模型名称');
    return;
  }
  testing.value = true;
  try {
    const { data } = await aiConfigApi.test({
      apiKey: form.apiKey.trim(),
      modelName: form.modelName.trim()
    });
    ElMessage.success(data.message || '连接成功');
    await loadConfig();
    prioritySwitchRef.value?.reload();
  } finally {
    testing.value = false;
  }
}

async function saveConfig() {
  if (!form.modelName.trim()) {
    ElMessage.warning('请填写模型名称');
    return;
  }
  saving.value = true;
  try {
    await aiConfigApi.save({
      apiKey: form.apiKey.trim(),
      modelName: form.modelName.trim(),
      enabled: form.enabled
    });
    ElMessage.success('设置已保存');
    await loadConfig();
    prioritySwitchRef.value?.reload();
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.ai-settings-page {
  min-height: 100%;
  padding: 24px;
  background: #f6f8fb;
}

.settings-title {
  max-width: 1040px;
  margin: 0 auto 18px;

  h2 {
    margin: 0 0 6px;
    color: #1f2937;
    font-size: 22px;
    font-weight: 650;
  }

  p {
    margin: 0;
    color: #6b7280;
    font-size: 14px;
  }
}

.settings-grid {
  max-width: 1040px;
  margin: 0 auto;
}

.settings-card,
.guide-card {
  margin-bottom: 24px;
  border: 1px solid #e7ebf3;
  border-radius: 16px;
  background: #fff;
  box-shadow: none;
}

:deep(.el-card__header) {
  padding: 18px 20px;
  border-bottom: 1px solid #e7ebf3;
}

:deep(.el-card__body) {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;

  h2,
  h3 {
    margin: 4px 0 0;
    color: #1f2937;
    font-size: 18px;
    font-weight: 650;
  }
}

.card-eyebrow {
  font-size: 12px;
  color: #5b7cfa;
  font-weight: 600;
}

.soft-tag {
  color: #5b7cfa;
  background: #e8edff;
  border-color: #e8edff;
  border-radius: 999px;
}

.field-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #9ca3af;
}

.basic-settings {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.basic-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 48px;
  padding: 12px 0;
  border-bottom: 1px solid #eef2f7;
}

.basic-row:last-child {
  border-bottom: 0;
}

.basic-label {
  color: #6b7280;
}

.basic-value {
  color: #1f2937;
  font-weight: 500;
}

.profile-edit {
  display: grid;
  grid-template-columns: minmax(180px, 260px) auto;
  gap: 10px;
  align-items: center;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.guide-list {
  margin: 0 0 18px;
  padding-left: 18px;
  color: #6b7280;
  line-height: 1.8;
}

.feature-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 16px;
  margin-bottom: 12px;
  background: #f9fafc;
  border: 1px solid #e7ebf3;
  border-radius: 14px;
}

.feature-item:last-child {
  margin-bottom: 0;
}

.feature-info {
  flex: 1;
  padding-right: 20px;
}

.feature-title {
  font-size: 15px;
  font-weight: 650;
  color: #1f2937;
  margin-bottom: 4px;
}

.feature-desc {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}

.feature-control {
  flex-shrink: 0;
}

.switch-control {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #6b7280;
  font-size: 13px;
}

.soft-btn {
  height: 36px;
  padding: 0 16px;
  border-radius: 10px;
  font-weight: 500;
}

.soft-btn.primary {
  color: #fff;
  background: #5b7cfa;
  border-color: #5b7cfa;
}

.soft-btn.primary:hover {
  background: #4667e8;
  border-color: #4667e8;
}

.soft-btn.secondary {
  color: #4b5563;
  background: #fff;
  border-color: #dde3ee;
}

.soft-btn.secondary:hover {
  background: #f2f5fb;
  border-color: #d7dfed;
}

:deep(.el-input__wrapper) {
  min-height: 40px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px #dde3ee inset;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #5b7cfa inset;
}

:deep(.el-form-item__label) {
  color: #4b5563;
  font-weight: 600;
}

:deep(.el-switch.is-checked .el-switch__core) {
  background-color: #5b7cfa;
  border-color: #5b7cfa;
}

:deep(.el-switch__core) {
  background-color: #d1d5db;
  border-color: #d1d5db;
}

@media (max-width: 900px) {
  .basic-row,
  .feature-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-edit {
    width: 100%;
    grid-template-columns: 1fr auto;
  }
}
</style>
