<template>
  <div class="layout">
    <!-- 左侧固定侧边栏（液态玻璃） -->
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-logo">L</div>
        <span class="brand-name">LifeDesk</span>
      </div>

      <nav class="nav-group">
        <div class="nav-title">工作台</div>
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="nav-item-active"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <!-- 今日健康迷你卡 -->
      <div class="health-mini">
        <div class="mini-title">今日健康</div>
        <div class="mini-row">
          <span class="mini-key">步数</span>
          <span class="mini-val">8,425 / 10k · 84%</span>
        </div>
        <div class="mini-row">
          <span class="mini-key">消耗</span>
          <span class="mini-val text-positive">-320 kcal · 赤字</span>
        </div>
        <div class="mini-row">
          <span class="mini-key">体重</span>
          <span class="mini-val">65.2 kg · -0.8</span>
        </div>
      </div>

      <div class="user-area">
        <div class="avatar">林</div>
        <span class="nickname">林深</span>
      </div>
    </aside>

    <!-- 右侧主区域 -->
    <div class="main">
      <header class="topbar">
        <div class="page-title">{{ pageTitle }}</div>
        <el-input class="search-box" placeholder="搜索代词、任务、健身记录…" clearable>
          <template #suffix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-badge :value="3" class="bell">
          <el-icon :size="20"><Bell /></el-icon>
        </el-badge>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import {
  Search,
  Bell,
  HomeFilled,
  Folder,
  List,
  Calendar,
  Monitor,
  Trophy,
  Collection,
  Reading,
  Sunny
} from "@element-plus/icons-vue";

defineOptions({ name: "MainLayout" });

const route = useRoute();
const pageTitle = computed(() => (route.meta.title as string) || "LifeDesk");

const navItems = [
  { path: "/dashboard", label: "首页", icon: HomeFilled },
  { path: "/project", label: "项目管理", icon: Folder },
  { path: "/task", label: "任务工单", icon: List },
  { path: "/schedule", label: "日程会议", icon: Calendar },
  { path: "/monitor", label: "系统监控", icon: Monitor },
  { path: "/skill", label: "Skills 工坊", icon: Trophy },
  { path: "/bookmark", label: "收藏与笔记", icon: Collection },
  { path: "/english", label: "英语提升", icon: Reading },
  { path: "/health", label: "个人健康", icon: Sunny }
];
</script>

<style scoped lang="scss">
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

// 侧栏：半透明深紫 + 玻璃模糊，让右侧页面渐变光斑透过
.sidebar {
  width: 240px;
  flex-shrink: 0;
  background: $bg-sidebar;
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border-right: 1px solid $border-glass;
  display: flex;
  flex-direction: column;
  padding: 18px 0;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 22px 22px;
}
.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, $accent, $accent-light);
  font-weight: 700;
  font-size: 18px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(167, 139, 250, 0.35);
}
.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: $text-primary;
}

.nav-group {
  padding: 8px 12px;
  flex: 1;
  overflow-y: auto;
}
.nav-title {
  padding: 8px 12px 4px;
  font-size: 11px;
  color: $text-tertiary;
  letter-spacing: 1px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  font-size: 14px;
  color: $text-secondary;
  border-radius: 10px;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.15s;
  margin-bottom: 2px;
  &:hover {
    background: rgba(167, 139, 250, 0.08);
    color: $text-primary;
  }
}
.nav-item-active {
  background: rgba(167, 139, 250, 0.16);
  color: $accent-light;
  border: 1px solid $border-accent;
}

.health-mini {
  margin: 6px 14px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid $border-glass;
  border-radius: $radius-sm;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}
.mini-title {
  font-size: 11px;
  color: $text-tertiary;
  margin-bottom: 6px;
  letter-spacing: 1px;
}
.mini-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  line-height: 1.7;
  color: $text-secondary;
}

.user-area {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 22px 6px;
  border-top: 1px solid $border-glass;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, $accent, $accent-light);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(167, 139, 250, 0.3);
}
.nickname {
  font-size: 14px;
  color: $text-primary;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
// 顶栏：玻璃风格（让 content 的渐变光斑透过顶栏）
.topbar {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 22px;
  background: rgba(11, 8, 23, 0.55);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid $border-glass;
}
.page-title {
  font-size: 17px;
  font-weight: 600;
  color: $text-primary;
  min-width: 120px;
}
.search-box {
  flex: 1;
  max-width: 520px;
}
.bell {
  margin-left: auto;
  cursor: pointer;
  color: $text-secondary;
}
.content {
  flex: 1;
  overflow-y: auto;
  padding: 22px;
}
</style>
