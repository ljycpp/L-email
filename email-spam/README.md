# 垃圾邮件模型训练工具

这个目录现在只负责垃圾邮件模型的训练、评估和导出，不再作为运行时 Web 服务依赖。

## 目录作用

- `train.py`：训练并导出模型
- `predict.py`：本地命令行测试模型
- `models/spam_model.json`：训练产出的模型文件
- `spam_filter/`：特征提取、分类器、训练逻辑

## 运行方式

训练模型：

```powershell
python .\train.py
```

命令行预测：

```powershell
python .\predict.py --subject "测试主题" --body "测试正文"
```

## 与后端的关系

- 训练完成后，将 `models/spam_model.json` 同步到：
  - `mail-system-backend/src/main/resources/spam_model.json`
- 后端启动时会直接加载这个模型文件
- 邮件到达时由后端内置插件执行垃圾邮件识别

## 说明

如果你只需要运行 L-email 系统，不需要单独启动这里的 Python 服务。
