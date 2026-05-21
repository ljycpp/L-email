# L-email 系统前端

前端基于 `Vue 3 + Vite + Element Plus + Pinia + Vue Router 4`。

## 功能概览

- 邮件列表、详情、写信、回复、转发、草稿编辑
- 联系人、分组、标签
- AI 助手右侧抽屉
- WebSocket 新邮件提醒
- 本地缓存、筛选持久化
- 垃圾邮件自动过滤与优先级开关

## 启动

```powershell
Set-Location ".\vue-mail-front"
npm install
npm run dev
```

默认地址：

- 前端：`http://localhost:8081`
- 后端：`http://localhost:8080`

## 构建

```powershell
npm run build
npm run preview
```

## 默认账号

- `admin / 123456`
- 完整邮箱为 `admin@lmailbox.com`

## 重要页面

- 收件箱：邮件查看、筛选、右键快捷操作
- 写信页：附件上传、AI 回复插入
- AI 设置：第三方模型接入配置
- 联系人 / 分组：新建、编辑、删除、组内发信

## 智能能力

- AI 助手：摘要、回复建议、待办提取
- 垃圾邮件自动过滤：依赖 Python 服务
- 邮件优先级判断：依赖第三方大模型接入配置

## 启动前检查

1. 后端已启动
2. 垃圾邮件服务已启动（如需测试垃圾过滤）
3. `.env` 中 `VITE_MAIL_EMAIL_DOMAIN` 与后端域名一致

## 产品命名

前端展示名统一为 **L-email 系统**。
