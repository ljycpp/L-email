<template>
  <Teleport to="body">
    <div v-show="userStore.token" class="new-mail-toast-host" aria-live="polite">
      <TransitionGroup name="mail-toast" tag="div" class="new-mail-toast-stack">
        <div
          v-for="item in notificationStore.toasts"
          :key="item.id"
          class="new-mail-toast"
          :class="{ 'is-spam': item.kind === 'spam', 'is-priority': item.kind === 'priority' }"
          role="alert"
          @click="openMail(item)"
        >
          <div class="toast-icon" :class="{ 'is-spam': item.kind === 'spam', 'is-priority': item.kind === 'priority' }">
            <el-icon :size="22"><Message /></el-icon>
          </div>
          <div class="toast-content">
            <div class="toast-header">
              <span class="toast-brand">{{ MAIL_SYSTEM_NAME }}</span>
              <span class="toast-tag" :class="{ 'is-spam': item.kind === 'spam', 'is-priority': item.kind === 'priority' }">
                {{ item.kind === 'spam' ? '自动过滤' : item.kind === 'priority' ? '高优先级' : '新邮件' }}
              </span>
            </div>
            <div class="toast-sender">{{ item.sender }}</div>
            <div class="toast-subject">{{ item.title }}</div>
            <div v-if="item.kind === 'spam' && item.reason" class="toast-reason">{{ item.reason }}</div>
            <div v-else-if="item.kind === 'priority' && item.priorityScore != null" class="toast-reason">
              优先级评分：{{ Math.round(item.priorityScore * 100) }}%
            </div>
            <div v-else-if="item.senderMail" class="toast-mail">{{ item.senderMail }}</div>
          </div>
          <button
            type="button"
            class="toast-close"
            aria-label="关闭"
            @click.stop="notificationStore.removeToast(item.id)"
          >
            <el-icon><Close /></el-icon>
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { Close, Message } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import { useNotificationStore } from '@/stores/notification';
import { useMailStore } from '@/stores/mail';
import { useUserStore } from '@/stores/user';
import { MAIL_SYSTEM_NAME } from '@/constants/brand';

const router = useRouter();
const userStore = useUserStore();
const notificationStore = useNotificationStore();
const mailStore = useMailStore();

function openMail(item) {
  if (!item.mailId) {
    return;
  }
  notificationStore.removeToast(item.id);
  mailStore.setMailId(item.mailId);
  mailStore.setMailType('receive');
  if (item.kind === 'spam') {
    router.push('/spam');
    return;
  }
  router.push('/mail_detail');
}
</script>

<style scoped lang="scss">
.new-mail-toast-host {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 5000;
  pointer-events: none;
  max-width: calc(100vw - 32px);
}

.new-mail-toast-stack {
  display: flex;
  flex-direction: column-reverse;
  align-items: flex-end;
  gap: 10px;
}

.new-mail-toast {
  pointer-events: auto;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 340px;
  max-width: 100%;
  padding: 14px 12px 14px 14px;
  background: #fff;
  border-radius: 8px;
  box-shadow:
    0 6px 24px rgba(0, 0, 0, 0.12),
    0 0 0 1px rgba(0, 0, 0, 0.04);
  cursor: pointer;

  &:hover {
    box-shadow:
      0 8px 28px rgba(0, 0, 0, 0.16),
      0 0 0 1px rgba(64, 158, 255, 0.2);
  }
}

.toast-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff 0%, #1a73e8 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toast-content {
  flex: 1;
  min-width: 0;
}

.toast-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.toast-brand {
  font-size: 12px;
  font-weight: 600;
  color: #303133;
}

.toast-tag {
  font-size: 11px;
  color: #409eff;
  background: #ecf5ff;
  padding: 1px 6px;
  border-radius: 3px;

  &.is-spam {
    color: #e6a23c;
    background: #fdf6ec;
  }

  &.is-priority {
    color: #f56c6c;
    background: #fef0f0;
  }
}

.new-mail-toast.is-spam {
  border-left: 3px solid #e6a23c;
}

.new-mail-toast.is-priority {
  border-left: 3px solid #f56c6c;
}

.toast-icon.is-spam {
  background: linear-gradient(135deg, #e6a23c 0%, #f56c6c 100%);
}

.toast-icon.is-priority {
  background: linear-gradient(135deg, #f56c6c 0%, #c0392b 100%);
}

.toast-reason {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.toast-sender {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toast-subject {
  font-size: 13px;
  color: #606266;
  margin-top: 2px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.toast-mail {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toast-close {
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: #c0c4cc;
  padding: 2px;
  cursor: pointer;
  border-radius: 4px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    color: #606266;
    background: #f5f7fa;
  }
}

/* TransitionGroup 进入/离开 */
.mail-toast-enter-active,
.mail-toast-leave-active {
  transition: all 0.32s cubic-bezier(0.22, 1, 0.36, 1);
}

.mail-toast-enter-from {
  transform: translateX(120%);
  opacity: 0;
}

.mail-toast-leave-to {
  transform: translateX(40px);
  opacity: 0;
}

.mail-toast-move {
  transition: transform 0.32s ease;
}
</style>
