import { createRouter, createWebHashHistory, type RouteRecordRaw } from "vue-router";

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    component: () => import("@/layout/MainLayout.vue"),
    redirect: "/dashboard",
    children: [
      {
        path: "dashboard",
        name: "Dashboard",
        component: () => import("@/views/dashboard/index.vue"),
        meta: { title: "首页" }
      },
      {
        path: "project",
        name: "Project",
        component: () => import("@/views/project/index.vue"),
        meta: { title: "项目管理" }
      },
      {
        path: "task",
        name: "Task",
        component: () => import("@/views/task/index.vue"),
        meta: { title: "任务工单" }
      },
      {
        path: "schedule",
        name: "Schedule",
        component: () => import("@/views/schedule/index.vue"),
        meta: { title: "日程会议" }
      },
      {
        path: "monitor",
        name: "Monitor",
        component: () => import("@/views/monitor/index.vue"),
        meta: { title: "系统监控" }
      },
      {
        path: "skill",
        name: "Skill",
        component: () => import("@/views/skill/index.vue"),
        meta: { title: "Skills 工坊" }
      },
      {
        path: "bookmark",
        name: "Bookmark",
        component: () => import("@/views/bookmark/index.vue"),
        meta: { title: "收藏与笔记" }
      },
      {
        path: "english",
        name: "English",
        component: () => import("@/views/english/index.vue"),
        meta: { title: "英语提升" }
      },
      {
        path: "health",
        name: "Health",
        component: () => import("@/views/health/index.vue"),
        meta: { title: "个人健康" }
      }
    ]
  }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes
});

export default router;