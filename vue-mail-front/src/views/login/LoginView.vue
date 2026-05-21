<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">{{ MAIL_SYSTEM_NAME }} 登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin">
        <el-form-item prop="emailInput">
          <el-input
            v-model="form.emailInput"
            placeholder="请输入账号名或完整邮箱"
            :prefix-icon="Message"
          />
          <div class="field-hint">
            邮箱后缀固定为 {{ MAIL_EMAIL_SUFFIX }}。仅输入账号名时将自动补全后缀。
          </div>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>

      <p class="tips demo-line">
        演示账号：<code>admin</code> / 密码 <code>123456</code>，完整邮箱：
        <code>{{ demoEmail }}</code>
      </p>
      <p class="tips">
        还没有账号？
        <el-link type="primary" @click="router.push('/register')">立即注册</el-link>
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { Message, Lock } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';
import { MAIL_SYSTEM_NAME, MAIL_EMAIL_SUFFIX, buildMailboxEmail } from '@/constants/brand';

const router = useRouter();
const userStore = useUserStore();
const menuStore = useMenuStore();
const formRef = ref();
const loading = ref(false);

const LOCAL_MAILBOX = /^[a-zA-Z0-9]([a-zA-Z0-9._-]{0,62}[a-zA-Z0-9])?$/;
const SIMPLE_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const form = reactive({
  emailInput: 'admin',
  password: '123456'
});

const demoEmail = computed(() => buildMailboxEmail('admin'));

function resolveLoginEmail(raw) {
  const value = String(raw ?? '').trim();
  if (!value) {
    return '';
  }
  if (!value.includes('@')) {
    return buildMailboxEmail(value);
  }
  return value.toLowerCase();
}

const validateEmailInput = (rule, value, callback) => {
  const input = String(value ?? '').trim();
  if (!input) {
    callback(new Error('请输入账号名或邮箱地址'));
    return;
  }
  if (!input.includes('@')) {
    if (!LOCAL_MAILBOX.test(input)) {
      callback(new Error('账号名仅可包含字母、数字、点、下划线和连字符'));
      return;
    }
    callback();
    return;
  }
  if (!SIMPLE_EMAIL.test(input)) {
    callback(new Error('邮箱格式不正确'));
    return;
  }
  callback();
};

const rules = {
  emailInput: [{ validator: validateEmailInput, trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }]
};

async function handleLogin() {
  await formRef.value.validate();
  loading.value = true;
  try {
    const email = resolveLoginEmail(form.emailInput);
    await userStore.login(email, form.password);
    await userStore.fetchProfile();
    menuStore.loaded = false;
    await menuStore.loadMenus();
    router.push('/inbox');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d3557, #457b9d);
}

.login-card {
  width: 400px;
  padding: 12px;
}

.title {
  text-align: center;
  margin: 0 0 24px;
}

.submit-btn {
  width: 100%;
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.35;
}

.tips {
  margin-top: 16px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.demo-line code {
  font-size: 12px;
  padding: 0 4px;
  background: #f5f7fa;
  border-radius: 3px;
}
</style>
