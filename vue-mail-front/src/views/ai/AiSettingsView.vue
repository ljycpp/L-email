<template>
  <div class="app-container ai-settings-page">
    <el-row :gutter="20">
      <el-col :lg="14" :md="16" :sm="24">
        <el-card shadow="never" class="settings-card">
          <template #header>
            <div class="card-header">
              <div>
                <div class="card-eyebrow">{{ MAIL_SYSTEM_NAME }}</div>
                <h2>AI 设置</h2>
              </div>
              <el-tag type="info">VVEAI</el-tag>
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
              <el-switch v-model="form.enabled" />
            </el-form-item>

            <div class="actions">
              <el-button :loading="testing" @click="testConfig">测试连接</el-button>
              <el-button type="primary" :loading="saving" @click="saveConfig">保存设置</el-button>
            </div>
          </el-form>
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
import { MAIL_SYSTEM_NAME } from '@/constants/brand';

const saving = ref(false);
const testing = ref(false);

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

onMounted(loadConfig);

async function loadConfig() {
  const { data } = await aiConfigApi.get();
  Object.assign(config, data || {});
  form.apiKey = '';
  form.modelName = data?.modelName || '';
  form.enabled = Boolean(data?.enabled);
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
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.ai-settings-page {
  padding-top: 20px;
}

.settings-card,
.guide-card {
  border-radius: 18px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;

  h2,
  h3 {
    margin: 4px 0 0;
    color: #303133;
  }
}

.card-eyebrow {
  font-size: 12px;
  letter-spacing: 0.06em;
  color: #1a73e8;
  font-weight: 600;
}

.field-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.actions {
  display: flex;
  gap: 12px;
}

.guide-list {
  margin: 0 0 18px;
  padding-left: 18px;
  color: #606266;
  line-height: 1.8;
}
</style>
