const loginForm = document.getElementById("login-form");
const loginUsername = document.getElementById("login-username");
const loginPassword = document.getElementById("login-password");
const loginSubmit = document.getElementById("login-submit");
const loginError = document.getElementById("login-error");

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
    throw error;
  }
  return data;
}

function setButtonBusy(button, busy, idleText, busyText) {
  button.disabled = busy;
  button.textContent = busy ? busyText : idleText;
}

async function bootstrap() {
  try {
    await requestJson("/api/session");
    window.location.href = "/";
  } catch (error) {
    if (error.status !== 401) {
      loginError.hidden = false;
      loginError.textContent = error.message;
    }
  }
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  loginError.hidden = true;
  setButtonBusy(loginSubmit, true, "进入系统", "登录中...");
  try {
    await requestJson("/api/login", {
      method: "POST",
      body: JSON.stringify({
        username: loginUsername.value.trim(),
        password: loginPassword.value,
      }),
    });
    window.location.href = "/";
  } catch (error) {
    loginError.hidden = false;
    loginError.textContent = error.message;
  } finally {
    setButtonBusy(loginSubmit, false, "进入系统", "登录中...");
  }
});

bootstrap();
