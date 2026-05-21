# L-email 系统垃圾邮件检测服务

该模块是 L-email 系统的 Python 垃圾邮件检测服务，负责：

- 文本垃圾邮件识别
- 为 Java 后端提供 `/api/login`、`/api/predict-text` 等接口
- 支撑“垃圾邮件自动过滤”开关

## 环境

- Python 3.7+
- 当前版本仅依赖 Python 标准库，无需额外安装第三方包

## 启动

```powershell
Set-Location ".\email-spam"
python .\web_app.py
```

默认地址：

```text
http://127.0.0.1:8000
```

## 默认登录

- 用户名：`admin`
- 密码：`admin123`

## 与后端对接

后端配置在：

- [application.yml](../mail-system-backend/src/main/resources/application.yml)

默认值：

- `spam-detector.base-url = http://127.0.0.1:8000`
- `spam-detector.username = admin`
- `spam-detector.password = admin123`

## 常用接口

- `POST /api/login`
- `POST /api/predict-text`
- `POST /api/predict-eml`
- `GET /api/status`

## 测试建议

1. 先单独打开 `http://127.0.0.1:8000`
2. 用默认账号登录
3. 手工输入主题和正文，确认能返回垃圾/正常结果
4. 再回到 L-email 系统前端开启垃圾邮件自动过滤开关
