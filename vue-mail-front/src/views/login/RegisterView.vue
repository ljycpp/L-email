<template>
  <div class="register-page">
    <el-card class="register-card">
      <h2 class="title">注册 {{ MAIL_SYSTEM_NAME }} 账号</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleRegister">
        <el-form-item label="邮箱账号" prop="localEmail">
          <el-input v-model="form.localEmail" placeholder="请输入账号名（不含 @）" :prefix-icon="Message">
            <template #append>{{ MAIL_EMAIL_SUFFIX }}</template>
          </el-input>
          <div class="field-hint">系统邮箱后缀固定为 {{ MAIL_EMAIL_SUFFIX }}</div>
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称（最多30字符）" :prefix-icon="UserFilled" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码至少6位" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" show-password :prefix-icon="Lock" @keyup.enter="handleRegister" />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleRegister">注册</el-button>
      </el-form>
      <p class="tips">已有账号？<el-link type="primary" @click="router.push('/login')">立即登录</el-link></p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { Message, Lock, UserFilled } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { register } from '@/api/auth';
import { MAIL_SYSTEM_NAME, MAIL_EMAIL_SUFFIX, buildMailboxEmail } from '@/constants/brand';

const router = useRouter();
const formRef = ref();
const loading = ref(false);

const LOCAL_MAILBOX = /^[a-zA-Z0-9]([a-zA-Z0-9._-]{0,62}[a-zA-Z0-9])?$/;

const form = reactive({
  localEmail: '',
  nickname: '',
  password: '',
  confirmPassword: ''
});

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次密码不一致'));
  } else {
    callback();
  }
};

const validateLocal = (rule, value, callback) => {
  const v = String(value ?? '').trim();
  if (!v) {
    callback(new Error('请输入邮箱账号'));
    return;
  }
  if (v.includes('@')) {
    callback(new Error('只需填写 @ 前面的账号名'));
    return;
  }
  if (!LOCAL_MAILBOX.test(v)) {
    callback(new Error('账号支持字母数字及 . _ -，首尾须为字母或数字'));
    return;
  }
  callback();
};

const rules = {
  localEmail: [{ validator: validateLocal, trigger: 'blur' }],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 30, message: '昵称最多30字符', trigger: 'blur' }
  ],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
};

async function handleRegister() {
  await formRef.value.validate();
  loading.value = true;
  try {
    const email = buildMailboxEmail(form.localEmail);
    await register(email, form.password, form.nickname);
    ElMessage.success('注册成功，请登录');
    router.push('/login');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped lang="scss">
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d3557, #457b9d);
}

.register-card {
  width: 420px;
  padding: 12px;
}

.title {
  text-align: center;
  margin: 0 0 24px;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.tips {
  margin-top: 16px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
</style>
