<template>
  <div class="task-board">
    <div v-for="col in columns" :key="col.key" class="board-col">
      <div class="col-head">
        <span>{{ col.label }}</span>
        <span class="col-count">{{ col.items.length }}</span>
        <span class="col-subnote" :class="col.noteCls">{{ col.subnote }}</span>
      </div>

      <div
        v-for="t in col.items"
        :key="t.id"
        class="task-card"
        :class="{ 'task-urgent': t.priority === 'urgent' }"
        @click="moveTask(t, col.key)"
      >
        <div class="task-title">
          <span class="status-dot" :class="statusDotCls(t, col.key)" />
          {{ t.title }}
        </div>
        <div class="task-bottom">
          <span class="task-due">{{ t.dueLabel }}</span>
          <StatusBadge :type="t.assignState === 'unassigned' ? 'warning' : 'neutral'">
            {{ t.assignState === "unassigned" ? "未指派" : "已指派" }}
          </StatusBadge>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { ElMessage } from "element-plus";
import StatusBadge from "@/components/StatusBadge.vue";
import { fetchTaskBoard, moveTask } from "@/api/task";

defineOptions({ name: "TaskView" });

const board = ref<{ todo: any[]; doing: any[]; done: any[] }>({
  todo: [],
  doing: [],
  done: []
});

async function load() {
  try {
    const r = await fetchTaskBoard();
    board.value = r.data;
  } catch {}
}

const columns = computed(() => {
  const unassignedTodo = board.value.todo.filter(t => t.assignState === "unassigned").length;
  const overdueDoing = 3;
  const doneTotal = board.value.done.length;
  const allTotal = board.value.todo.length + board.value.doing.length + doneTotal;
  const completion = allTotal > 0 ? Math.round((doneTotal / allTotal) * 100) : 0;
  return [
    {
      key: "todo",
      label: "待办",
      items: board.value.todo,
      subnote: `未指派 ${unassignedTodo}`,
      noteCls: "text-warning"
    },
    {
      key: "doing",
      label: "进行中",
      items: board.value.doing,
      subnote: `${overdueDoing} 项超期`,
      noteCls: "text-warning"
    },
    {
      key: "done",
      label: "已完成",
      items: board.value.done,
      subnote: `完成率 ${completion}%`,
      noteCls: "text-positive"
    }
  ];
});

function statusDotCls(t: any, col: string) {
  if (t.priority === "urgent") return "dot-danger";
  if (col === "todo") return "dot-accent";
  if (col === "doing") return "dot-accent";
  return "dot-positive";
}

async function moveTask(t: any, from: string) {
  const next = from === "todo" ? "doing" : from === "doing" ? "done" : "done";
  if (next === from) return;
  try {
    await moveTask(t.id, next);
    ElMessage.success(`已移动到「${next === "todo" ? "待办" : next === "doing" ? "进行中" : "已完成"}」`);
    await load();
  } catch {}
}

onMounted(load);
</script>

<style scoped lang="scss">
.task-board {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.board-col {
  background: $bg-card;
  border: 1px solid $border-subtle;
  border-radius: $radius-card;
  padding: 16px;
  min-height: 360px;
}

.col-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid $border-subtle;
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}
.col-count {
  font-size: 14px;
  color: $accent-light;
  font-weight: 700;
}
.col-subnote {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
}

.task-card {
  background: $bg-page;
  border: 1px solid $border-subtle;
  border-radius: $radius-sm;
  padding: 12px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover {
    border-color: $border-accent;
  }
}
.task-urgent {
  border-color: rgba(248, 113, 113, 0.6);
  background: rgba(248, 113, 113, 0.06);
  border-width: 2px;
}
.task-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: $text-primary;
  margin-bottom: 8px;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-danger {
  background: $color-danger;
}
.dot-accent {
  background: $accent;
}
.dot-positive {
  background: $color-positive;
}
.task-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.task-due {
  font-size: 12px;
  color: $text-secondary;
}
</style>