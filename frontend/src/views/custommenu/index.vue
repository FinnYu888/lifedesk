<template>
  <div class="custom-menu-page">
    <!-- 顶部操作条：菜单名 + 在新窗口打开 + 编辑/删除 -->
    <div class="ld-card top-bar">
      <div class="title-area">
        <el-icon class="title-icon"><Link /></el-icon>
        <span class="title-text">{{ menuName || "自定义菜单" }}</span>
        <span v-if="menuUrl" class="title-url">{{ menuUrl }}</span>
      </div>
      <div class="top-actions">
        <button class="ld-btn ld-btn-ghost" @click="openExternal">
          <el-icon><TopRight /></el-icon> 新窗口打开
        </button>
        <button v-if="menuId" class="ld-btn ld-btn-ghost" @click="onEdit">
          <el-icon><Edit /></el-icon> 编辑
        </button>
        <button v-if="menuId" class="ld-btn ld-btn-ghost danger" @click="onDelete">
          <el-icon><Delete /></el-icon> 删除
        </button>
      </div>
    </div>

    <!-- iframe 嵌入区：把目标网站嵌入到当前系统内展示，不跳转新窗口 -->
    <div class="ld-card frame-wrap">
      <div v-if="loading && menuUrl" class="loading">
        <el-icon class="loading-icon" :size="28"><Loading /></el-icon>
        <span>正在加载 {{ menuName }}…</span>
        <span class="loading-hint">如长时间无响应，可点击"新窗口打开"</span>
      </div>
      <iframe
        v-if="menuUrl"
        :key="menuUrl"
        :src="menuUrl"
        class="embed-frame"
        frameborder="0"
        allow="fullscreen; clipboard-read; clipboard-write; encrypted-media; autoplay; picture-in-picture"
        sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox allow-downloads allow-modals allow-presentation"
        referrerpolicy="no-referrer"
        @load="onLoad"
      />
      <div v-else class="empty">
        <el-icon :size="28"><Link /></el-icon>
        <span>未配置网站地址</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Link, Edit, Delete, TopRight, Loading } from "@element-plus/icons-vue";
import { useCustomMenus } from "@/composables/useCustomMenus";

defineOptions({ name: "CustomMenuView" });

const route = useRoute();
const router = useRouter();
const { findMenu, editCustomMenu, removeCustomMenu } = useCustomMenus();

const loading = ref(true);

// 路由 query 携带 id/name/url。优先用共享列表里的最新数据（编辑后立即反映），
// 找不到时回退到 query（首屏直链或列表为空的情况）
const menuId = ref<number | null>(null);
const menuName = ref<string>("");
const menuUrl = ref<string>("");

function syncFromRoute() {
  const id = route.query.id ? Number(route.query.id) : null;
  menuId.value = id;
  const found = findMenu(id);
  menuName.value = found?.name || (route.query.name as string) || "";
  menuUrl.value = found?.url || (route.query.url as string) || "";
  loading.value = true; // url 变了，重新进入 loading
}

syncFromRoute();
watch(() => route.query, syncFromRoute, { deep: true });

function onLoad() {
  loading.value = false;
}

function openExternal() {
  if (menuUrl.value) window.open(menuUrl.value, "_blank", "noopener,noreferrer");
}

function onEdit() {
  if (!menuId.value) return;
  // 复用 MainLayout 里的编辑弹窗：通过路由 query 触发
  // 这里直接走轻量内联编辑弹窗，避免跨组件耦合
  ElMessageBox.prompt("请输入菜单名称", "编辑菜单", {
    inputValue: menuName.value,
    inputPattern: /.+/,
    inputErrorMessage: "名称不能为空"
  })
    .then(async ({ value: name }) => {
      ElMessageBox.prompt("请输入网站地址（URL）", "网站地址", {
        inputValue: menuUrl.value,
        inputPattern: /^https?:\/\/.+/,
        inputErrorMessage: "请输入合法的 http(s) 地址"
      })
        .then(async ({ value: url }) => {
          await editCustomMenu(menuId.value!, { name, url });
          ElMessage.success("已保存");
          // 重新跳转以刷新当前页的 url/name
          router.replace({
            path: "/custom",
            query: { id: String(menuId.value!), name, url }
          });
        })
        .catch(() => {});
    })
    .catch(() => {});
}

async function onDelete() {
  if (!menuId.value) return;
  try {
    await ElMessageBox.confirm(`确定删除菜单「${menuName.value}」？`, "提示", {
      type: "warning"
    });
    await removeCustomMenu(menuId.value);
    ElMessage.success("已删除");
    router.replace("/dashboard");
  } catch {
    /* 取消或拦截器已提示 */
  }
}
</script>

<style scoped lang="scss">
.custom-menu-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  // 让 iframe 嵌入区填满 content 高度（content 本身有 padding: 22px）
  height: calc(100vh - 60px - 44px);
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 18px;
  flex-shrink: 0;
}
.title-area {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.title-icon {
  color: $accent-light;
  font-size: 16px;
}
.title-text {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  white-space: nowrap;
}
.title-url {
  font-size: 12px;
  color: $text-tertiary;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.top-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.frame-wrap {
  flex: 1;
  padding: 0;
  overflow: hidden;
  position: relative;
  display: flex;
}
.embed-frame {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}

.loading,
.empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: $text-tertiary;
  font-size: 13px;
  background: $bg-card;
  z-index: 1;
}
.loading-icon {
  color: $accent-light;
  animation: spin 1.1s linear infinite;
}
.loading-hint {
  font-size: 11px;
  color: $text-tertiary;
  margin-top: 4px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.ld-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid $border-glass;
  background: rgba(255, 255, 255, 0.04);
  color: $text-secondary;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover:not(:disabled) {
    background: rgba(167, 139, 250, 0.1);
    color: $text-primary;
  }
  &.danger:hover {
    color: #f87171;
  }
}
</style>
