import { onBeforeUnmount, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { useMailStore } from '@/stores/mail';
import { useNotificationStore } from '@/stores/notification';
import { buildMailSocketUrl } from '@/utils/mailSocket';
import { MAIL_SYSTEM_NAME } from '@/constants/brand';

let reconnectTimer = null;
let socket = null;
let visibilityBound = false;

/** 退出登录时调用，避免 MainLayout 还引用已经销毁的连接。 */
export function disconnectMailWebSocket() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  if (socket) {
    socket.onclose = null;
    socket.onerror = null;
    socket.onmessage = null;
    socket.close();
    socket = null;
  }
}

/** 登录后全局维持 WebSocket，任意页面都能收到新邮件提醒。 */
export function useMailWebSocket() {
  const router = useRouter();
  const userStore = useUserStore();
  const mailStore = useMailStore();
  const notificationStore = useNotificationStore();

  function scheduleReconnect() {
    if (!userStore.token || reconnectTimer) {
      return;
    }
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null;
      connectSocket();
    }, 3000);
  }

  function tryShowBrowserNotification(payload) {
    if (!('Notification' in window) || Notification.permission !== 'granted') {
      return;
    }
    if (document.visibilityState === 'visible') {
      return;
    }
    const isHighPriority = payload?.priorityLevel === 'HIGH';
    const title = isHighPriority
      ? `高优先级邮件：${payload.sender || '未知发件人'}`
      : `${payload.sender || '未知发件人'} - ${MAIL_SYSTEM_NAME}`;
    const body = isHighPriority
      ? `${payload.title || '（无主题）'}\n请优先处理这封邮件。`
      : (payload.title || '（无主题）');
    const n = new Notification(title, { body, tag: `mail-${payload.mailId}` });
    n.onclick = () => {
      window.focus();
      if (payload.mailId) {
        mailStore.setMailId(payload.mailId);
        mailStore.setMailType('receive');
        router.push('/mail_detail');
      }
      n.close();
    };
  }

  function handleSocketMessage(raw) {
    try {
      const payload = JSON.parse(raw.data);
      if (payload?.type === 'SPAM_FILTERED') {
        notificationStore.onSpamFiltered(payload);
        return;
      }
      if (payload?.type === 'NEW_MAIL') {
        notificationStore.onNewMail(payload);
        tryShowBrowserNotification(payload);
      }
    } catch {
      // 忽略格式错误的 WebSocket 消息
    }
  }

  function connectSocket() {
    if (!userStore.token || socket?.readyState === WebSocket.OPEN) {
      return;
    }
    disconnectMailWebSocket();
    const ws = new WebSocket(buildMailSocketUrl(userStore.token));
    ws.onmessage = handleSocketMessage;
    ws.onclose = () => {
      socket = null;
      scheduleReconnect();
    };
    ws.onerror = () => {
      ws.close();
    };
    socket = ws;
  }

  function requestNotificationPermission() {
    if (!('Notification' in window) || Notification.permission !== 'default') {
      return;
    }
    Notification.requestPermission().catch(() => {});
  }

  function onVisibilityChange() {
    if (document.visibilityState === 'visible' && userStore.token) {
      if (!socket || socket.readyState !== WebSocket.OPEN) {
        connectSocket();
      }
    }
  }

  watch(
    () => userStore.token,
    token => {
      if (token) {
        connectSocket();
      } else {
        disconnectMailWebSocket();
        notificationStore.clearAll();
      }
    },
    { immediate: true }
  );

  if (typeof document !== 'undefined' && !visibilityBound) {
    visibilityBound = true;
    document.addEventListener('visibilitychange', onVisibilityChange);
  }

  onBeforeUnmount(() => {
    // App 根组件通常不会卸载；退出登录请调用 disconnectMailWebSocket。
  });

  return {
    connectSocket,
    disconnectMailWebSocket,
    requestNotificationPermission
  };
}
