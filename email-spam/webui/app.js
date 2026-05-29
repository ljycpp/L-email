const percentFormatter = new Intl.NumberFormat("zh-CN", {
  style: "percent",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const dateFormatter = new Intl.DateTimeFormat("zh-CN", {
  dateStyle: "medium",
  timeStyle: "short",
});

const textForm = document.getElementById("text-form");
const emlForm = document.getElementById("eml-form");
const retrainForm = document.getElementById("retrain-form");
const createUserForm = document.getElementById("create-user-form");

const emlInput = document.getElementById("eml-input");
const emlFileName = document.getElementById("eml-file-name");
const logoutButton = document.getElementById("logout-button");
const refreshUsersButton = document.getElementById("refresh-users");

const subjectInput = document.getElementById("subject-input");
const bodyInput = document.getElementById("body-input");
const retrainPathInput = document.getElementById("retrain-path");
const newUsernameInput = document.getElementById("new-username");
const newDisplayNameInput = document.getElementById("new-display-name");
const newPasswordInput = document.getElementById("new-password");
const newRoleInput = document.getElementById("new-role");

const currentDisplayName = document.getElementById("current-display-name");
const currentRoleText = document.getElementById("current-role-text");
const adminPanel = document.getElementById("admin-panel");
const usersList = document.getElementById("users-list");

const resultPanel = document.getElementById("result-panel");
const resultBadge = document.getElementById("result-badge");
const resultLabel = document.getElementById("result-label");
const resultConfidence = document.getElementById("result-confidence");
const resultSubject = document.getElementById("result-subject");
const resultPreview = document.getElementById("result-preview");
const spamScore = document.getElementById("spam-score");
const hamScore = document.getElementById("ham-score");
const spamMeter = document.getElementById("spam-meter");
const hamMeter = document.getElementById("ham-meter");

const serverMode = document.getElementById("server-mode");
const modelPath = document.getElementById("model-path");
const dataPathText = document.getElementById("data-path-text");
const alphaText = document.getElementById("alpha-text");
const ratioText = document.getElementById("ratio-text");
const reportSamples = document.getElementById("report-samples");
const reportAccuracy = document.getElementById("report-accuracy");
const reportRecall = document.getElementById("report-recall");
const reportF1 = document.getElementById("report-f1");
const tokenList = document.getElementById("token-list");

const fillHamButton = document.getElementById("fill-ham");
const fillSpamButton = document.getElementById("fill-spam");
const textSubmit = document.getElementById("text-submit");
const emlSubmit = document.getElementById("eml-submit");
const retrainSubmit = document.getElementById("retrain-submit");
const createUserSubmit = document.getElementById("create-user-submit");

let currentSession = null;
let currentUsers = [];

function formatPercent(value) {
  return percentFormatter.format(Number(value || 0));
}

function formatDate(value) {
  if (!value) {
    return "--";
  }
  return dateFormatter.format(new Date(value * 1000));
}

function setButtonBusy(button, busy, idleText, busyText) {
  button.disabled = busy;
  button.textContent = busy ? busyText : idleText;
}

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, {
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });

  const data = await response.json();
  if (!response.ok) {
    const error = new Error(data.error || "请求失败");
    error.code = data.code;
    error.status = response.status;
    throw error;
  }
  return data;
}

function handleAuthError(error) {
  if (error && error.status === 401) {
    window.location.href = "/login";
    return true;
  }
  return false;
}

function renderSession(session) {
  currentSession = session;
  currentDisplayName.textContent = session.user.display_name;
  currentRoleText.textContent = `${session.user.role_text} · ${session.user.username}`;
  adminPanel.hidden = !session.permissions.manage_users;
  retrainForm.hidden = !session.permissions.retrain_model;
  retrainSubmit.disabled = !session.permissions.retrain_model;
}

function renderResult(result) {
  resultPanel.dataset.result = result.label || "idle";
  resultBadge.textContent = result.label_text || "等待输入";
  resultLabel.textContent = result.label_text || "请先提交一封邮件";
  resultConfidence.textContent = result.confidence_text || "--";
  resultSubject.textContent = result.subject || "空主题";
  resultPreview.textContent = result.body_preview || "未提取到正文";

  const spam = Number(result.scores && result.scores.spam ? result.scores.spam : 0);
  const ham = Number(result.scores && result.scores.ham ? result.scores.ham : 0);
  spamScore.textContent = formatPercent(spam);
  hamScore.textContent = formatPercent(ham);
  spamMeter.style.width = `${Math.round(spam * 100)}%`;
  hamMeter.style.width = `${Math.round(ham * 100)}%`;
}

function renderTrainingReport(report) {
  if (!report) {
    reportSamples.textContent = "--";
    reportAccuracy.textContent = "--";
    reportRecall.textContent = "--";
    reportF1.textContent = "--";
    return;
  }

  reportSamples.textContent = String(report.sample_count || "--");
  reportAccuracy.textContent = report.metrics ? formatPercent(report.metrics.accuracy) : "--";
  reportRecall.textContent = report.metrics ? formatPercent(report.metrics.recall) : "--";
  reportF1.textContent = report.metrics ? formatPercent(report.metrics.f1) : "--";
}

function renderTokens(tokens) {
  if (!tokens || !tokens.length) {
    tokenList.innerHTML = "<li><strong>暂无数据</strong><span>--</span></li>";
    return;
  }

  tokenList.innerHTML = tokens
    .map((item) => `<li><strong>${escapeHtml(item.token)}</strong><span>${item.score.toFixed(2)}</span></li>`)
    .join("");
}

function renderStatus(status) {
  serverMode.textContent = status.model_exists ? "模型已就绪" : "等待模型";
  modelPath.textContent = status.model_path || "--";
  dataPathText.textContent = status.data_path || "--";
  alphaText.textContent = String(status.alpha || "--");
  ratioText.textContent = formatPercent(status.test_ratio || 0);
  retrainPathInput.value = status.data_path || "";
  renderTrainingReport(status.last_training_report);
  renderTokens(status.top_tokens);
}

function renderUsers(users) {
  currentUsers = users || [];

  if (!currentUsers.length) {
    usersList.innerHTML = '<div class="empty-card">当前还没有用户。</div>';
    return;
  }

  usersList.innerHTML = currentUsers
    .map(
      (user) => `
        <article class="user-card ${user.enabled ? "" : "user-card-disabled"}">
          <div class="user-card-head">
            <div>
              <h4>${escapeHtml(user.display_name)}</h4>
              <p>${escapeHtml(user.username)}</p>
            </div>
            <span class="user-role-tag">${escapeHtml(user.role_text)}</span>
          </div>
          <div class="user-meta">
            <span>${escapeHtml(user.enabled_text)}</span>
            <span>创建于 ${escapeHtml(formatDate(user.created_at))}</span>
          </div>
          <div class="user-controls">
            <label class="field compact-field">
              <span>显示名称</span>
              <input type="text" data-action="display" data-user-id="${user.id}" value="${escapeHtml(user.display_name)}" />
            </label>
            <label class="field compact-field">
              <span>角色</span>
              <select data-action="role" data-user-id="${user.id}">
                <option value="analyst" ${user.role === "analyst" ? "selected" : ""}>分析员</option>
                <option value="admin" ${user.role === "admin" ? "selected" : ""}>管理员</option>
              </select>
            </label>
            <label class="toggle-row">
              <input type="checkbox" data-action="enabled" data-user-id="${user.id}" ${user.enabled ? "checked" : ""} />
              <span>启用账号</span>
            </label>
          </div>
          <div class="action-row user-actions">
            <button type="button" class="ghost-button user-save-button" data-user-id="${user.id}">保存修改</button>
            <button type="button" class="secondary-button user-reset-button" data-user-id="${user.id}">重置密码</button>
          </div>
        </article>
      `
    )
    .join("");
}

function getUserDraft(userId) {
  const displayInput = usersList.querySelector(`input[data-action="display"][data-user-id="${userId}"]`);
  const roleInput = usersList.querySelector(`select[data-action="role"][data-user-id="${userId}"]`);
  const enabledInput = usersList.querySelector(`input[data-action="enabled"][data-user-id="${userId}"]`);

  return {
    display_name: displayInput ? displayInput.value.trim() : "",
    role: roleInput ? roleInput.value : "analyst",
    enabled: enabledInput ? enabledInput.checked : false,
  };
}

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  const chunkSize = 0x8000;
  let binary = "";
  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize);
    binary += String.fromCharCode.apply(null, chunk);
  }
  return btoa(binary);
}

async function loadUsers() {
  if (!currentSession || !currentSession.permissions.manage_users) {
    return;
  }
  const result = await requestJson("/api/users");
  renderUsers(result.users);
}

fillHamButton.addEventListener("click", () => {
  subjectInput.value = "周会时间调整";
  bodyInput.value = "本周产品周会调整到周三下午三点，请大家提前准备进度汇报。";
});

fillSpamButton.addEventListener("click", () => {
  subjectInput.value = "限时领取红包";
  bodyInput.value = "点击链接立即领取188元新人奖励，仅限今天，过期不补。";
});

emlInput.addEventListener("change", () => {
  const file = emlInput.files && emlInput.files[0];
  emlFileName.textContent = file ? `已选择：${file.name}` : "尚未选择文件";
});

logoutButton.addEventListener("click", async () => {
  try {
    await requestJson("/api/logout", { method: "POST", body: JSON.stringify({}) });
  } finally {
    window.location.href = "/login";
  }
});

textForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setButtonBusy(textSubmit, true, "开始识别", "识别中...");
  try {
    const result = await requestJson("/api/predict-text", {
      method: "POST",
      body: JSON.stringify({
        subject: subjectInput.value.trim(),
        body: bodyInput.value.trim(),
      }),
    });
    renderResult(result);
  } catch (error) {
    if (!handleAuthError(error)) {
      resultLabel.textContent = error.message;
      resultBadge.textContent = "请求失败";
    }
  } finally {
    setButtonBusy(textSubmit, false, "开始识别", "识别中...");
  }
});

emlForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const file = emlInput.files && emlInput.files[0];
  if (!file) {
    resultLabel.textContent = "请先选择一个 .eml 文件。";
    resultBadge.textContent = "缺少文件";
    return;
  }

  setButtonBusy(emlSubmit, true, "识别邮件文件", "解析中...");
  try {
    const buffer = await file.arrayBuffer();
    const result = await requestJson("/api/predict-eml", {
      method: "POST",
      body: JSON.stringify({
        filename: file.name,
        content_base64: arrayBufferToBase64(buffer),
      }),
    });
    renderResult(result);
  } catch (error) {
    if (!handleAuthError(error)) {
      resultLabel.textContent = error.message;
      resultBadge.textContent = "请求失败";
    }
  } finally {
    setButtonBusy(emlSubmit, false, "识别邮件文件", "解析中...");
  }
});

retrainForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setButtonBusy(retrainSubmit, true, "一键重新训练模型", "训练中...");
  try {
    const status = await requestJson("/api/retrain", {
      method: "POST",
      body: JSON.stringify({
        data_path: retrainPathInput.value.trim(),
      }),
    });
    renderStatus(status);
    resultBadge.textContent = "训练完成";
    resultLabel.textContent = "模型已重新训练，可继续识别新邮件。";
  } catch (error) {
    if (!handleAuthError(error)) {
      resultBadge.textContent = "训练失败";
      resultLabel.textContent = error.message;
    }
  } finally {
    setButtonBusy(retrainSubmit, false, "一键重新训练模型", "训练中...");
  }
});

createUserForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setButtonBusy(createUserSubmit, true, "创建用户", "创建中...");
  try {
    const result = await requestJson("/api/users", {
      method: "POST",
      body: JSON.stringify({
        username: newUsernameInput.value.trim(),
        display_name: newDisplayNameInput.value.trim(),
        password: newPasswordInput.value,
        role: newRoleInput.value,
      }),
    });
    renderUsers(result.users);
    newUsernameInput.value = "";
    newDisplayNameInput.value = "";
    newPasswordInput.value = "";
    newRoleInput.value = "analyst";
  } catch (error) {
    if (!handleAuthError(error)) {
      alert(error.message);
    }
  } finally {
    setButtonBusy(createUserSubmit, false, "创建用户", "创建中...");
  }
});

refreshUsersButton.addEventListener("click", async () => {
  try {
    await loadUsers();
  } catch (error) {
    if (!handleAuthError(error)) {
      alert(error.message);
    }
  }
});

usersList.addEventListener("click", async (event) => {
  const saveButton = event.target.closest(".user-save-button");
  const resetButton = event.target.closest(".user-reset-button");

  if (saveButton) {
    const userId = saveButton.dataset.userId;
    const draft = getUserDraft(userId);
    setButtonBusy(saveButton, true, "保存修改", "保存中...");
    try {
      const result = await requestJson("/api/users/update", {
        method: "POST",
        body: JSON.stringify({
          user_id: userId,
          display_name: draft.display_name,
          role: draft.role,
          enabled: draft.enabled,
        }),
      });
      renderUsers(result.users);
    } catch (error) {
      if (!handleAuthError(error)) {
        alert(error.message);
      }
    } finally {
      setButtonBusy(saveButton, false, "保存修改", "保存中...");
    }
  }

  if (resetButton) {
    const userId = resetButton.dataset.userId;
    const nextPassword = window.prompt("请输入新密码（至少 6 位）");
    if (!nextPassword) {
      return;
    }
    setButtonBusy(resetButton, true, "重置密码", "重置中...");
    try {
      const result = await requestJson("/api/users/reset-password", {
        method: "POST",
        body: JSON.stringify({
          user_id: userId,
          password: nextPassword,
        }),
      });
      renderUsers(result.users);
      alert("密码已重置。");
    } catch (error) {
      if (!handleAuthError(error)) {
        alert(error.message);
      }
    } finally {
      setButtonBusy(resetButton, false, "重置密码", "重置中...");
    }
  }
});

async function bootstrap() {
  try {
    const [session, status] = await Promise.all([requestJson("/api/session"), requestJson("/api/status")]);
    renderSession(session);
    renderStatus(status);
    if (session.permissions.manage_users) {
      await loadUsers();
    }
  } catch (error) {
    if (!handleAuthError(error)) {
      serverMode.textContent = "状态读取失败";
      resultBadge.textContent = "初始化失败";
      resultLabel.textContent = error.message;
    }
  }
}

bootstrap();
