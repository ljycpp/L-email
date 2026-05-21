const percentFormatter = new Intl.NumberFormat("zh-CN", {
  style: "percent",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const statusText = document.getElementById("analytics-status");
const displayName = document.getElementById("analytics-display-name");
const roleText = document.getElementById("analytics-role-text");
const logoutButton = document.getElementById("analytics-logout-button");
const refreshButton = document.getElementById("refresh-analytics-button");

const metricSampleCount = document.getElementById("metric-sample-count");
const metricTrainCount = document.getElementById("metric-train-count");
const metricTestCount = document.getElementById("metric-test-count");
const metricSpamRatio = document.getElementById("metric-spam-ratio");

const metricAccuracy = document.getElementById("metric-accuracy");
const metricPrecision = document.getElementById("metric-precision");
const metricRecall = document.getElementById("metric-recall");
const metricF1 = document.getElementById("metric-f1");

const distributionRing = document.getElementById("distribution-ring");
const ringCenterValue = document.getElementById("ring-center-value");
const legendSpamCount = document.getElementById("legend-spam-count");
const legendHamCount = document.getElementById("legend-ham-count");

const matrixGrid = document.getElementById("matrix-grid");
const spamCloud = document.getElementById("spam-cloud");
const hamCloud = document.getElementById("ham-cloud");
const spamSignals = document.getElementById("spam-signals");
const hamSignals = document.getElementById("ham-signals");

function formatPercent(value) {
  return percentFormatter.format(Number(value || 0));
}

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function setButtonBusy(button, busy, idleText, busyText) {
  button.disabled = busy;
  button.textContent = busy ? busyText : idleText;
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
    error.status = response.status;
    error.code = data.code;
    throw error;
  }
  return data;
}

function handleNavigationError(error) {
  if (error.status === 401) {
    window.location.href = "/login";
    return true;
  }
  if (error.status === 403) {
    window.location.href = "/";
    return true;
  }
  return false;
}

function renderSession(session) {
  displayName.textContent = session.user.display_name;
  roleText.textContent = `${session.user.role_text} · ${session.user.username}`;
}

function renderOverview(report) {
  metricSampleCount.textContent = String(report.sample_count || "--");
  metricTrainCount.textContent = String(report.train_count || "--");
  metricTestCount.textContent = String(report.test_count || "--");
  metricSpamRatio.textContent = formatPercent(report.distribution.spam_ratio || 0);

  legendSpamCount.textContent = `${report.distribution.spam_count} 封`;
  legendHamCount.textContent = `${report.distribution.ham_count} 封`;
  ringCenterValue.textContent = formatPercent(report.distribution.spam_ratio || 0);

  const spamAngle = Math.round((report.distribution.spam_ratio || 0) * 360);
  distributionRing.style.background = `conic-gradient(var(--danger) 0deg ${spamAngle}deg, var(--safe) ${spamAngle}deg 360deg)`;
}

function renderMetrics(metrics) {
  if (!metrics) {
    metricAccuracy.textContent = "--";
    metricPrecision.textContent = "--";
    metricRecall.textContent = "--";
    metricF1.textContent = "--";
    return;
  }

  metricAccuracy.textContent = formatPercent(metrics.accuracy);
  metricPrecision.textContent = formatPercent(metrics.precision);
  metricRecall.textContent = formatPercent(metrics.recall);
  metricF1.textContent = formatPercent(metrics.f1);
}

function renderMatrix(confusionMatrix) {
  const cells = [
    {
      title: "TP",
      subtitle: "实际垃圾 -> 预测垃圾",
      value: confusionMatrix.tp,
      className: "matrix-good",
    },
    {
      title: "FN",
      subtitle: "实际垃圾 -> 预测正常",
      value: confusionMatrix.fn,
      className: "matrix-bad",
    },
    {
      title: "FP",
      subtitle: "实际正常 -> 预测垃圾",
      value: confusionMatrix.fp,
      className: "matrix-warn",
    },
    {
      title: "TN",
      subtitle: "实际正常 -> 预测正常",
      value: confusionMatrix.tn,
      className: "matrix-good",
    },
  ];

  const maxValue = Math.max(...cells.map((cell) => cell.value), 1);
  matrixGrid.innerHTML = cells
    .map((cell) => {
      const intensity = 0.22 + (cell.value / maxValue) * 0.58;
      return `
        <article class="matrix-cell ${cell.className}" style="--cell-alpha:${intensity.toFixed(3)}">
          <span class="matrix-code">${cell.title}</span>
          <strong>${cell.value}</strong>
          <p>${cell.subtitle}</p>
        </article>
      `;
    })
    .join("");
}

function renderCloud(container, items, emptyText) {
  if (!items || !items.length) {
    container.innerHTML = `<span class="cloud-empty">${emptyText}</span>`;
    return;
  }

  container.innerHTML = items
    .map(
      (item) => `
        <span class="cloud-chip" style="--weight:${item.weight}">
          ${escapeHtml(item.text)}
          <small>${item.count}</small>
        </span>
      `
    )
    .join("");
}

function renderSignals(container, items, barClass) {
  if (!items || !items.length) {
    container.innerHTML = '<div class="empty-card">暂无高判别信号。</div>';
    return;
  }

  const maxScore = Math.max(...items.map((item) => item.score), 1);
  container.innerHTML = items
    .map(
      (item) => `
        <div class="signal-row">
          <div class="signal-head">
            <strong>${escapeHtml(item.text)}</strong>
            <span>${item.score.toFixed(2)}</span>
          </div>
          <div class="signal-bar">
            <span class="${barClass}" style="width:${((item.score / maxScore) * 100).toFixed(1)}%"></span>
          </div>
        </div>
      `
    )
    .join("");
}

function renderReport(report) {
  renderOverview(report);
  renderMetrics(report.metrics);
  renderMatrix(report.confusion_matrix);
  renderCloud(spamCloud, report.word_clouds.spam, "暂无垃圾邮件词云");
  renderCloud(hamCloud, report.word_clouds.ham, "暂无正常邮件词云");
  renderSignals(spamSignals, report.indicative_terms.spam, "signal-fill-danger");
  renderSignals(hamSignals, report.indicative_terms.ham, "signal-fill-safe");
  statusText.textContent = "分析数据已更新";
}

async function loadAnalytics() {
  const [session, report] = await Promise.all([requestJson("/api/session"), requestJson("/api/analytics")]);
  renderSession(session);
  renderReport(report);
}

logoutButton.addEventListener("click", async () => {
  try {
    await requestJson("/api/logout", { method: "POST", body: JSON.stringify({}) });
  } finally {
    window.location.href = "/login";
  }
});

refreshButton.addEventListener("click", async () => {
  setButtonBusy(refreshButton, true, "刷新分析", "刷新中...");
  try {
    await loadAnalytics();
  } catch (error) {
    if (!handleNavigationError(error)) {
      statusText.textContent = error.message;
    }
  } finally {
    setButtonBusy(refreshButton, false, "刷新分析", "刷新中...");
  }
});

async function bootstrap() {
  try {
    await loadAnalytics();
  } catch (error) {
    if (!handleNavigationError(error)) {
      statusText.textContent = error.message;
    }
  }
}

bootstrap();
