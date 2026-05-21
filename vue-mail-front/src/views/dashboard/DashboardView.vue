<template>
  <div class="app-container">
    <el-card>
      <h2>欢迎使用 L-email 系统</h2>
      <p>请从左侧菜单进入收件箱、写邮件、联系人、分组或智能助手相关功能。</p>
      <el-space>
        <el-button type="primary" @click="$router.push('/inbox')">进入收件箱</el-button>
        <el-button @click="$router.push('/mail_send')">开始写邮件</el-button>
      </el-space>
    </el-card>
  </div>
</template>
