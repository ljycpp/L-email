# L-email 系统后端

后端基于 `Spring Boot 3 + MyBatis-Plus + MySQL`，负责用户、邮件、附件、标签、通讯录、AI 助手与 WebSocket 通知。

## 主要功能

- JWT 登录鉴权，兼容 `X-Token`
- 收件箱 / 发件箱 / 草稿箱 / 回收站 / 垃圾邮件箱
- 联系人、分组、标签管理
- 附件上传下载
- WebSocket 新邮件通知与高优先级提醒
- AI 助手配置、摘要、回复建议、待办提取
- 垃圾邮件自动过滤、邮件优先级自动判断

## 启动前准备

1. 创建数据库 `mail_system`
2. 执行 [schema.sql](./src/main/resources/db/schema.sql)
3. 已有旧库时，按需执行：
   - `migration_spam_auto_filter.sql`
   - `migration_priority_auto_filter.sql`
4. 创建附件目录：

```text
D:\mail-system-storage\attachments
```

5. 设置数据库密码环境变量：

```powershell
$env:MAIL_DB_PASSWORD="123456"
```

## 启动方式

推荐直接运行主类：

- [MailSystemApplication.java](./src/main/java/com/practice/mailsystem/MailSystemApplication.java)

命令行编译与启动可使用 `scripts/start-backend.ps1`。

## 关键配置

配置文件：[application.yml](./src/main/resources/application.yml)

- `mail-system.email-domain`：邮箱域名，当前为 `lmailbox.com`
- `app.storage.root-path`：附件存储目录
- `app.ai.*`：第三方 AI 配置
- `spam-detector.model-path`：垃圾邮件模型文件路径
- `spam-detector.spam-threshold`：垃圾阈值

## 垃圾邮件检测说明

垃圾邮件检测采用“训练与部署分离”的方式：

- `email-spam` 目录仅用于训练和更新模型
- 运行时由后端直接加载 `classpath:spam_model.json`
- 不再依赖独立 Python 推理服务

## 默认账号

- `admin@lmailbox.com / 123456`
- `alice@lmailbox.com / 123456`
- `bob@lmailbox.com / 123456`
- `carol@lmailbox.com / 123456`

## 模块说明

- `ai`：AI 助手模块
- `spam`：垃圾邮件检测与自动过滤
- `priority`：邮件优先级判断
- `websocket`：实时通知
- `directory`：联系人与分组
- `mail`：邮件核心业务
