<template>
  <div class="daily-briefing">
    <!-- ===== 今日任务 ===== -->
    <section class="panel tasks-panel">
      <div class="panel-head">
        <div class="title-block">
          <span class="title-bar" />
          <h3 class="panel-title">今日任务</h3>
          <span class="date-badge">{{ dateLabel }}</span>
        </div>
        <span class="panel-subnote">
          需求 {{ reqTasks.length }} · 健康目标 {{ healthGoals.length }} · 英语待完成 {{ englishTasks.length }}
        </span>
        <button class="ld-btn ld-btn-ghost refresh-btn" :disabled="tasksLoading" @click="loadTodayTasks">
          <el-icon v-if="tasksLoading" class="is-loading"><Loading /></el-icon>
          <el-icon v-else><Refresh /></el-icon>
          刷新
        </button>
      </div>
      <div class="task-groups">
        <!-- 我的需求：title + content -->
        <div class="task-group req-group">
          <div class="group-label">
            <span class="group-dot dot-req" />我的需求 · 待办/进行中
            <span class="group-count">{{ reqTasks.length }}</span>
          </div>
          <div class="group-list">
            <template v-if="reqTasks.length">
              <div v-for="t in reqTasks" :key="'req-' + t.id" class="req-item">
                <div class="req-line1">
                  <span class="st-dot" :class="t.taskStatus === 'todo' ? 'st-todo' : 'st-doing'" />
                  <span class="item-title" :title="t.topic">{{ t.topic }}</span>
                  <span class="status-tag" :class="t.taskStatus === 'todo' ? 'tag-todo' : 'tag-doing'">
                    {{ t.taskStatus === "todo" ? "待办" : "进行中" }}
                  </span>
                </div>
                <div v-if="t.taskDetailPlain" class="req-content" :title="t.taskDetailPlain">
                  {{ t.taskDetailPlain }}
                </div>
              </div>
            </template>
            <div v-else class="group-empty">
              暂无待办/进行中的需求{{ reqFailed ? "（需求平台鉴权可能已过期）" : "" }}
            </div>
          </div>
        </div>

        <!-- 个人健康：每日目标 -->
        <div class="task-group">
          <div class="group-label">
            <span class="group-dot dot-health" />个人健康 · 每日目标
            <span class="group-count">{{ healthGoals.length }}</span>
          </div>
          <div class="group-list">
            <template v-if="healthGoals.length">
              <div v-for="(g, i) in healthGoals" :key="'hp-' + i" class="task-item">
                <span class="item-title" :title="g.label || g.scope">{{ g.label || g.scope }}</span>
                <span class="goal-value">{{ g.goalValue }}{{ g.unit || "" }}</span>
              </div>
            </template>
            <div v-else class="group-empty">暂未设置健康每日目标</div>
          </div>
        </div>

        <!-- 英语提升：今日待完成 -->
        <div class="task-group">
          <div class="group-label">
            <span class="group-dot dot-english" />英语提升 · 今日待完成
            <span class="group-count">{{ englishTasks.length }}</span>
          </div>
          <div class="group-list">
            <template v-if="englishTasks.length">
              <div v-for="t in englishTasks" :key="'en-' + t.id" class="task-item">
                <span class="item-title" :title="t.name">{{ t.name }}</span>
                <span class="goal-value" :title="t.dailyGoal">{{ t.dailyGoal || "待打卡" }}</span>
              </div>
            </template>
            <div v-else class="group-empty text-positive">今日英语任务已全部完成 🎉</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 三列资讯区 ===== -->
    <div class="feed-grid">
      <!-- GitHub Top 10 -->
      <section class="panel feed-panel">
        <div class="panel-head">
          <div class="title-block">
            <span class="title-bar bar-github" />
            <h3 class="panel-title">GitHub Top 10</h3>
          </div>
          <div class="period-tabs">
            <button
              v-for="p in PERIODS"
              :key="p.value"
              class="period-tab"
              :class="{ active: githubPeriod === p.value }"
              @click="switchGithubPeriod(p.value)"
            >
              {{ p.label }}
            </button>
          </div>
          <button class="ld-btn ld-btn-ghost refresh-btn icon-btn" :disabled="githubLoading" @click="loadGithub">
            <el-icon v-if="githubLoading" class="is-loading"><Loading /></el-icon>
            <el-icon v-else><Refresh /></el-icon>
          </button>
        </div>
        <div class="feed-list">
          <template v-if="githubRepos.length">
            <a
              v-for="(repo, i) in githubRepos"
              :key="repo.fullName"
              class="feed-item"
              :href="repo.url"
              target="_blank"
              rel="noopener"
            >
              <span class="rank" :class="'rank-' + (i < 3 ? i + 1 : 'n')">{{ i + 1 }}</span>
              <div class="item-main">
                <div class="item-title repo-name">{{ repo.fullName }}</div>
                <div class="item-desc" :title="repo.description">{{ repo.description || "（无描述）" }}</div>
              </div>
              <div class="item-side">
                <span class="star">★ {{ formatStars(repo.stars) }}</span>
                <span v-if="repo.language" class="lang">
                  <span class="lang-dot" :style="{ background: langColor(repo.language) }" />{{ repo.language }}
                </span>
              </div>
            </a>
          </template>
          <div v-else class="group-empty">{{ githubLoading ? "加载中…" : "暂无数据" }}</div>
        </div>
      </section>

      <!-- NBA 比赛 -->
      <section class="panel feed-panel">
        <div class="panel-head">
          <div class="title-block">
            <span class="title-bar bar-nba" />
            <h3 class="panel-title">NBA 比赛</h3>
            <span class="group-count">虎扑</span>
          </div>
          <button class="ld-btn ld-btn-ghost refresh-btn icon-btn" :disabled="nbaLoading" @click="loadNba">
            <el-icon v-if="nbaLoading" class="is-loading"><Loading /></el-icon>
            <el-icon v-else><Refresh /></el-icon>
          </button>
        </div>
        <div class="feed-list">
          <template v-if="nbaNews.length">
            <a
              v-for="(n, i) in nbaNews"
              :key="n.url"
              class="feed-item"
              :href="n.url"
              target="_blank"
              rel="noopener"
            >
              <span class="rank" :class="'rank-' + (i < 3 ? i + 1 : 'n')">{{ i + 1 }}</span>
              <div class="item-main">
                <div class="item-title" :title="n.title">{{ n.title }}</div>
              </div>
            </a>
          </template>
          <div v-else class="group-empty">{{ nbaLoading ? "加载中…" : "暂无数据" }}</div>
        </div>
      </section>

      <!-- 今日头条 -->
      <section class="panel feed-panel">
        <div class="panel-head">
          <div class="title-block">
            <span class="title-bar bar-toutiao" />
            <h3 class="panel-title">今日头条</h3>
            <span class="group-count">热榜</span>
          </div>
          <button class="ld-btn ld-btn-ghost refresh-btn icon-btn" :disabled="toutiaoLoading" @click="loadToutiao">
            <el-icon v-if="toutiaoLoading" class="is-loading"><Loading /></el-icon>
            <el-icon v-else><Refresh /></el-icon>
          </button>
        </div>
        <div class="feed-list">
          <template v-if="toutiaoList.length">
            <a
              v-for="(t, i) in toutiaoList"
              :key="t.url + i"
              class="feed-item"
              :href="t.url"
              target="_blank"
              rel="noopener"
            >
              <span class="rank" :class="'rank-' + (i < 3 ? i + 1 : 'n')">{{ i + 1 }}</span>
              <div class="item-main">
                <div class="item-title" :title="t.title">{{ t.title }}</div>
              </div>
              <div class="item-side">
                <span v-if="t.hotValue" class="hot-value">{{ t.hotValue }}</span>
              </div>
            </a>
          </template>
          <div v-else class="group-empty">{{ toutiaoLoading ? "加载中…" : "暂无数据" }}</div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { Refresh, Loading } from "@element-plus/icons-vue";
import { fetchRequirements, type Requirement } from "@/api/requirement";
import { fetchDailyGoals, type DailyGoal } from "@/api/dailyGoal";
import { fetchEnglishTasks, type EnglishTask } from "@/api/english";
import {
  fetchGithubTrending,
  fetchNbaNews,
  fetchToutiaoHot,
  type GithubRepo,
  type GithubPeriod,
  type NbaNewsItem,
  type ToutiaoItem
} from "@/api/dailyBriefing";

defineOptions({ name: "TaskView" });

// ===== 日期徽标 =====
const WEEKDAYS = ["日", "一", "二", "三", "四", "五", "六"];
const now = new Date();
const dateLabel = `${now.getMonth() + 1}月${now.getDate()}日 · 星期${WEEKDAYS[now.getDay()]}`;

// ===== 今日任务 =====
const reqTasks = ref<(Requirement & { taskDetailPlain: string })[]>([]);
const healthGoals = ref<DailyGoal[]>([]);
const englishTasks = ref<EnglishTask[]>([]);
const tasksLoading = ref(false);
const reqFailed = ref(false);

/** 去除 HTML 标签取纯文本 */
function htmlToText(html: string): string {
  if (!html) return "";
  return html
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/\s+/g, " ")
    .trim();
}

async function loadTodayTasks() {
  tasksLoading.value = true;
  // Promise.allSettled：三路数据互不影响，任一失败不阻塞其它
  const [reqRes, healthRes, englishRes] = await Promise.allSettled([
    fetchRequirements(),
    fetchDailyGoals("health"),
    fetchEnglishTasks()
  ]);

  if (reqRes.status === "fulfilled") {
    const list: Requirement[] = Array.isArray(reqRes.value?.data) ? reqRes.value.data : [];
    reqTasks.value = list
      .filter(r => r.taskStatus === "todo" || r.taskStatus === "doing")
      .map(r => ({ ...r, taskDetailPlain: htmlToText(r.taskDetail || "") }));
    reqFailed.value = false;
  } else {
    reqTasks.value = [];
    reqFailed.value = true;
  }

  healthGoals.value =
    healthRes.status === "fulfilled" ? (healthRes.value?.data?.current ?? []) : [];

  if (englishRes.status === "fulfilled") {
    const list: EnglishTask[] = Array.isArray(englishRes.value?.data) ? englishRes.value.data : [];
    const today = new Date().toISOString().substring(0, 10);
    englishTasks.value = list.filter(t => t.lastDoneDate !== today);
  } else {
    englishTasks.value = [];
  }

  tasksLoading.value = false;
}

// ===== GitHub Top 10 =====
const PERIODS: { label: string; value: GithubPeriod }[] = [
  { label: "24小时", value: "day" },
  { label: "一周", value: "week" },
  { label: "30天", value: "month" }
];
const githubPeriod = ref<GithubPeriod>("day");
const githubRepos = ref<GithubRepo[]>([]);
const githubLoading = ref(false);

async function loadGithub() {
  githubLoading.value = true;
  try {
    const r = await fetchGithubTrending(githubPeriod.value);
    githubRepos.value = Array.isArray(r?.data) ? r.data : [];
  } catch {
    githubRepos.value = [];
  }
  githubLoading.value = false;
}

function switchGithubPeriod(p: GithubPeriod) {
  if (githubPeriod.value === p) return;
  githubPeriod.value = p;
  loadGithub();
}

function formatStars(n: number): string {
  return n >= 1000 ? (n / 1000).toFixed(1) + "k" : String(n);
}

/** 常见语言色点（GitHub 语言色） */
const LANG_COLORS: Record<string, string> = {
  TypeScript: "#3178c6",
  JavaScript: "#f1e05a",
  Python: "#3572A5",
  Java: "#b07219",
  Go: "#00ADD8",
  Rust: "#dea584",
  "C++": "#f34b7d",
  C: "#555555",
  "C#": "#178600",
  Vue: "#41b883",
  HTML: "#e34c26",
  CSS: "#563d7c",
  Shell: "#89e051",
  Kotlin: "#A97BFF",
  Swift: "#F05138"
};
function langColor(lang: string): string {
  return LANG_COLORS[lang] || "#8b7ff5";
}

// ===== NBA 比赛 =====
const nbaNews = ref<NbaNewsItem[]>([]);
const nbaLoading = ref(false);

async function loadNba() {
  nbaLoading.value = true;
  try {
    const r = await fetchNbaNews();
    nbaNews.value = Array.isArray(r?.data) ? r.data : [];
  } catch {
    nbaNews.value = [];
  }
  nbaLoading.value = false;
}

// ===== 今日头条 =====
const toutiaoList = ref<ToutiaoItem[]>([]);
const toutiaoLoading = ref(false);

async function loadToutiao() {
  toutiaoLoading.value = true;
  try {
    const r = await fetchToutiaoHot();
    toutiaoList.value = Array.isArray(r?.data) ? r.data : [];
  } catch {
    toutiaoList.value = [];
  }
  toutiaoLoading.value = false;
}

onMounted(() => {
  loadTodayTasks();
  loadGithub();
  loadNba();
  loadToutiao();
});
</script>

<style scoped lang="scss">
// ===== 一屏布局：顶栏 60px + 页面上下 padding 44px =====
.daily-briefing {
  height: calc(100vh - 60px - 44px);
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.panel {
  background: $bg-card;
  border: 1px solid $border-subtle;
  border-radius: $radius-card;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  flex-shrink: 0;
}

.title-block {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.title-bar {
  width: 4px;
  height: 14px;
  border-radius: 2px;
  background: linear-gradient(180deg, $accent-light, $accent);
}
.bar-github {
  background: linear-gradient(180deg, #7ee0d2, #2ea89a);
}
.bar-nba {
  background: linear-gradient(180deg, #ff9a6c, #ef6c4d);
}
.bar-toutiao {
  background: linear-gradient(180deg, #f66c6c, #d93025);
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
  white-space: nowrap;
}
.panel-subnote {
  font-size: 12px;
  color: $text-secondary;
  white-space: nowrap;
}

.date-badge {
  font-size: 11px;
  color: $accent-light;
  background: rgba(139, 127, 245, 0.14);
  border: 1px solid rgba(139, 127, 245, 0.3);
  padding: 1px 8px;
  border-radius: 10px;
  white-space: nowrap;
}

.refresh-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 4px 10px;
  flex-shrink: 0;
}
.icon-btn {
  padding: 4px 8px;
}

// ===== 今日任务分组 =====
.tasks-panel {
  flex: 0 0 42%;
}

.task-groups {
  flex: 1;
  min-height: 0;
  display: grid;
  // 需求组占双倍宽度（title + content 内容更多）
  grid-template-columns: 2fr 1fr 1fr;
  gap: 12px;
}

.task-group {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: $bg-page;
  border: 1px solid $border-subtle;
  border-radius: $radius-sm;
  padding: 10px 12px;
}

.group-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 6px;
  flex-shrink: 0;
}
.group-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-req {
  background: $accent;
}
.dot-health {
  background: $color-positive;
}
.dot-english {
  background: #8b7ff5;
}
.group-count {
  margin-left: auto;
  font-size: 11px;
  font-weight: 700;
  color: $accent-light;
  background: rgba(139, 127, 245, 0.12);
  padding: 0 7px;
  line-height: 17px;
  border-radius: 9px;
  flex-shrink: 0;
}

.group-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.task-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed $border-subtle;
  &:last-child {
    border-bottom: none;
  }
}
.item-title {
  flex: 1;
  font-size: 13px;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.goal-value {
  flex-shrink: 0;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: $accent-light;
}
.group-empty {
  padding: 16px 0;
  text-align: center;
  font-size: 12px;
  color: $text-secondary;
}

// 需求条目：title 一行 + content 两行截断
.req-item {
  padding: 7px 0;
  border-bottom: 1px dashed $border-subtle;
  &:last-child {
    border-bottom: none;
  }
}
.req-line1 {
  display: flex;
  align-items: center;
  gap: 8px;
}
.req-content {
  margin: 3px 0 0 16px;
  font-size: 12px;
  line-height: 1.5;
  color: $text-secondary;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.st-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.st-todo {
  background: $color-warning;
}
.st-doing {
  background: $accent;
}

.status-tag {
  flex-shrink: 0;
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 10px;
}
.tag-todo {
  color: $color-warning;
  background: rgba(251, 191, 36, 0.12);
}
.tag-doing {
  color: $accent-light;
  background: rgba(139, 127, 245, 0.14);
}

// ===== 资讯三列 =====
.feed-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.feed-panel {
  min-height: 0;
}

.feed-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.feed-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 7px 6px;
  border-radius: $radius-sm;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
  &:hover {
    background: $bg-page;
    transform: translateX(2px);
    .item-title {
      color: $accent-light;
    }
  }
}

.rank {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 700;
  color: $text-secondary;
  background: $bg-page;
  margin-top: 1px;
}
.rank-1 {
  color: #fff;
  background: linear-gradient(135deg, #ff8a5c, #ef6c4d);
}
.rank-2 {
  color: #fff;
  background: linear-gradient(135deg, #f0b45c, #e69c3c);
}
.rank-3 {
  color: #fff;
  background: linear-gradient(135deg, #d9bd4a, #c9a227);
}

.item-main {
  flex: 1;
  min-width: 0;
}
.item-desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: $text-secondary;
  // 允许描述换行，最多 2 行截断（长描述不撑开展示区域）
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: normal;
  word-break: break-all;
}
.repo-name {
  font-weight: 600;
}

.item-side {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
}
.star {
  font-size: 12px;
  color: $color-warning;
  font-weight: 600;
}
.lang {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: $text-secondary;
}
.lang-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.hot-value {
  font-size: 11px;
  color: $color-danger;
}

// ===== 窄屏：允许滚动，退化为多行布局 =====
@media (max-width: 1280px) {
  .daily-briefing {
    height: auto;
    overflow: visible;
  }
  .feed-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .task-groups {
    grid-template-columns: repeat(2, 1fr);
  }
  .feed-list,
  .group-list {
    max-height: 420px;
  }
}
@media (max-width: 860px) {
  .feed-grid,
  .task-groups {
    grid-template-columns: 1fr;
  }
}
</style>
