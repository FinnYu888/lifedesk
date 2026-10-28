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
      },
      {
        // 常用App：桌面快捷方式风格的本机应用启动器，点击即真实启动（不嵌入窗口）
        path: "apps",
        name: "Apps",
        component: () => import("@/views/apps/index.vue"),
        meta: { title: "常用App" }
      },
      {
        // 我的音乐：外链/上传源文件，全局播放器播放不随菜单切换停止
        path: "music",
        name: "Music",
        component: () => import("@/views/music/index.vue"),
        meta: { title: "我的音乐" }
      },
      {
        // 自定义菜单嵌入页：通过 iframe 把目标网站嵌入当前系统展示
        // url/name/id 由 MainLayout 在 router.push 的 query 里携带
        path: "custom",
        name: "CustomMenu",
        component: () => import("@/views/custommenu/index.vue"),
        meta: { title: "自定义菜单" }
      }
    ]
  }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes
});

export default router;