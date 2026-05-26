<template>
  <div class="calendar-page">
    <section class="calendar-main">
      <div class="calendar-toolbar">
        <div>
          <h2>日历</h2>
          <p>{{ currentYear }} 年 {{ currentMonth + 1 }} 月</p>
        </div>
        <div class="calendar-actions">
          <el-button @click="goToday">今天</el-button>
          <el-button-group>
            <el-button @click="moveMonth(-1)">上个月</el-button>
            <el-button @click="moveMonth(1)">下个月</el-button>
          </el-button-group>
        </div>
      </div>

      <div class="week-row">
        <span v-for="day in weekDays" :key="day">{{ day }}</span>
      </div>

      <div class="month-grid">
        <button
          v-for="day in monthDays"
          :key="day.key"
          type="button"
          class="day-cell"
          :class="{ muted: !day.inMonth, today: day.key === todayKey, selected: day.key === selectedDate }"
          @click="selectedDate = day.key"
        >
          <span class="day-number">{{ day.date.getDate() }}</span>
          <span v-if="tasksByDate(day.key).length" class="task-count">{{ tasksByDate(day.key).length }}</span>
          <span v-if="tasksByDate(day.key).some(item => !item.done)" class="task-dot" />
        </button>
      </div>
    </section>

    <aside class="agenda-panel">
      <div class="agenda-header">
        <div>
          <h3>{{ selectedDate }}</h3>
          <p>{{ selectedTasks.length }} 个日程</p>
        </div>
      </div>

      <el-form class="task-form" @submit.prevent="addTask">
        <el-input v-model="taskForm.title" placeholder="添加日程或待办" maxlength="60" clearable @keyup.enter="addTask" />
        <div class="task-form-row">
          <el-time-picker v-model="taskForm.time" format="HH:mm" value-format="HH:mm" placeholder="时间" />
          <el-button type="primary" @click="addTask">添加</el-button>
        </div>
      </el-form>

      <div v-if="selectedTasks.length" class="task-list">
        <div v-for="task in selectedTasks" :key="task.id" class="task-item" :class="{ done: task.done }">
          <el-checkbox :model-value="task.done" @change="toggleTask(task)" />
          <div class="task-info">
            <span class="task-title">{{ task.title }}</span>
            <span v-if="task.time" class="task-time">{{ task.time }}</span>
          </div>
          <el-button text type="danger" @click="removeTask(task.id)">删除</el-button>
        </div>
      </div>
      <el-empty v-else description="这一天还没有日程" />
    </aside>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

const STORAGE_KEY = 'l-email-calendar-tasks';
const weekDays = ['一', '二', '三', '四', '五', '六', '日'];
const now = new Date();
const todayKey = formatDateKey(now);
const viewDate = ref(new Date(now.getFullYear(), now.getMonth(), 1));
const selectedDate = ref(todayKey);
const tasks = ref(readTasks());
const taskForm = reactive({ title: '', time: '' });

const currentYear = computed(() => viewDate.value.getFullYear());
const currentMonth = computed(() => viewDate.value.getMonth());

const monthDays = computed(() => {
  const year = currentYear.value;
  const month = currentMonth.value;
  const first = new Date(year, month, 1);
  const startOffset = (first.getDay() + 6) % 7;
  const start = new Date(year, month, 1 - startOffset);
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start);
    date.setDate(start.getDate() + index);
    return {
      date,
      key: formatDateKey(date),
      inMonth: date.getMonth() === month
    };
  });
});

const selectedTasks = computed(() => tasksByDate(selectedDate.value));

watch(tasks, value => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
}, { deep: true });

function moveMonth(step) {
  viewDate.value = new Date(currentYear.value, currentMonth.value + step, 1);
}

function goToday() {
  viewDate.value = new Date(now.getFullYear(), now.getMonth(), 1);
  selectedDate.value = todayKey;
}

function tasksByDate(date) {
  return tasks.value
    .filter(item => item.date === date)
    .sort((a, b) => String(a.time || '99:99').localeCompare(String(b.time || '99:99')));
}

function addTask() {
  const title = taskForm.title.trim();
  if (!title) {
    ElMessage.warning('请先输入日程内容');
    return;
  }
  tasks.value.push({
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    date: selectedDate.value,
    title,
    time: taskForm.time || '',
    done: false
  });
  taskForm.title = '';
  taskForm.time = '';
}

function toggleTask(task) {
  task.done = !task.done;
}

function removeTask(id) {
  tasks.value = tasks.value.filter(item => item.id !== id);
}

function readTasks() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function formatDateKey(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}
</script>

<style scoped lang="scss">
.calendar-page { height: 100%; display: grid; grid-template-columns: minmax(0, 1fr) 360px; background: var(--color-bg-page); }
.calendar-main { padding: 24px; overflow: auto; }
.calendar-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; h2 { margin: 0; font-size: 24px; color: var(--color-text-main); } p { margin: 4px 0 0; color: var(--color-text-secondary); } }
.calendar-actions { display: flex; gap: 10px; }
.week-row { display: grid; grid-template-columns: repeat(7, 1fr); color: var(--color-text-secondary); font-size: 13px; margin-bottom: 8px; span { padding: 0 10px; } }
.month-grid { display: grid; grid-template-columns: repeat(7, minmax(88px, 1fr)); gap: 8px; }
.day-cell { position: relative; min-height: 92px; padding: 10px; border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-bg-card); text-align: left; cursor: pointer; color: var(--color-text-main); transition: border-color .15s, background .15s, box-shadow .15s; &:hover { border-color: var(--color-primary); background: #f8faff; } &.muted { color: var(--color-text-muted); background: #f8fafc; } &.today { border-color: var(--color-primary); } &.selected { box-shadow: 0 0 0 2px rgba(64, 126, 255, .16); background: var(--color-primary-light); } }
.day-number { font-weight: 600; }
.task-count { position: absolute; right: 10px; top: 10px; font-size: 12px; color: var(--color-primary); }
.task-dot { position: absolute; left: 10px; bottom: 10px; width: 7px; height: 7px; border-radius: 50%; background: var(--color-primary); }
.agenda-panel { border-left: 1px solid var(--color-border); background: var(--color-bg-card); padding: 22px; overflow-y: auto; }
.agenda-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; h3 { margin: 0; font-size: 20px; } p { margin: 4px 0 0; color: var(--color-text-secondary); font-size: 13px; } }
.task-form { display: flex; flex-direction: column; gap: 10px; margin-bottom: 18px; }
.task-form-row { display: grid; grid-template-columns: 1fr auto; gap: 10px; }
.task-list { display: flex; flex-direction: column; gap: 10px; }
.task-item { display: flex; align-items: center; gap: 10px; padding: 10px; border: 1px solid var(--color-border); border-radius: 8px; &.done .task-title { color: var(--color-text-muted); text-decoration: line-through; } }
.task-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.task-title { color: var(--color-text-main); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-time { font-size: 12px; color: var(--color-text-secondary); }
@media (max-width: 980px) { .calendar-page { grid-template-columns: 1fr; } .agenda-panel { border-left: none; border-top: 1px solid var(--color-border); } .month-grid { grid-template-columns: repeat(7, minmax(42px, 1fr)); } .day-cell { min-height: 64px; } }
</style>
