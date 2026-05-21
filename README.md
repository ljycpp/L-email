# L-email 系统

L-email 系统是一个面向实训交付的邮件平台，包含以下模块：

- `vue-mail-front`：Vue 3 前端
- `mail-system-backend`：Spring Boot 后端
- `email-spam`：Python 垃圾邮件检测服务
- `docs`：启动说明、专题文档与答辩材料索引

## 文档导航

- [前端说明](./vue-mail-front/README.md)
- [后端说明](./mail-system-backend/README.md)
- [垃圾邮件服务说明](./email-spam/README.md)
- [文档索引](./docs/README.md)

## 系统能力

- 用户注册、登录、联系人、分组、标签
- 收件箱、发件箱、草稿箱、回收站、垃圾邮件箱
- 附件上传下载、多附件发送、草稿继续编辑
- WebSocket 新邮件提醒、高优先级提醒、本地缓存
- AI 助手：摘要、回复建议、待办提取
- 智能扩展：垃圾邮件检测、邮件优先级判断

## 快速启动顺序

1. 启动 MySQL，并准备 `mail_system` 数据库
2. 启动 `email-spam` Python 服务
3. 启动 `mail-system-backend`
4. 启动 `vue-mail-front`

详细步骤见各子模块 `README`。

## 演示账号

- `admin@lmailbox.com / 123456`
- `alice@lmailbox.com / 123456`
- `bob@lmailbox.com / 123456`
- `carol@lmailbox.com / 123456`

登录页也支持只输入账号名，例如 `admin`。

## 命名说明

当前对外展示名称统一为 **L-email 系统**。

- 前端标题、欢迎页、README、答辩文档统一使用 `L-email 系统`
- 后端包名、`artifactId`、数据库前缀等内部技术名称暂不修改，避免影响现有代码与配置
