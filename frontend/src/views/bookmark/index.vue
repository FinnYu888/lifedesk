<template>
  <div class="bookmark-page">
    <!-- 顶部搜索 + 操作 -->
    <div class="ld-card top-bar">
      <el-input
        v-model="keyword"
        class="search"
        placeholder="搜索名称、URL 或笔记内容…"
        clearable
        @input="onSearch"
      >
        <template #suffix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <div class="top-actions">
        <button class="ld-btn ld-btn-ghost" @click="openCreate('link')">
          <el-icon><Plus /></el-icon> 新建收藏
        </button>
        <button class="ld-btn ld-btn-ghost" @click="openCreate('note')">
          <el-icon><Plus /></el-icon> 新建笔记
        </button>
      </div>
    </div>

    <!-- 双栏：左=收藏，右=笔记 -->
    <div class="dual-pane">
      <!-- ========== 收藏：3D 立体环绕堆叠 ========== -->
      <div class="ld-card pane">
        <div class="pane-head">
          <div class="pane-head-left">
            <span class="pane-title">收藏</span>
            <span class="pane-count">{{ links.length }} 项</span>
          </div>
          <div class="pane-tip">悬停弹出 · 拖拽顶部卡片抽出展开</div>
        </div>
        <div class="pane-body">
          <div v-if="links.length === 0" class="empty">暂无收藏，点击右上「新建收藏」</div>
          <div v-else class="card-stack" @mouseleave="hoverIdx = -1">
            <!-- 堆叠卡片层（按 index 由远到近渲染，近的覆盖远的） -->
            <div
              v-for="(item, i) in links"
              :key="item.id"
              class="stack-card link-card"
              :class="{
                top: i === 0,
                hovered: hoverIdx === i,
                dragging: dragIdx === i,
                dim: hoverIdx >= 0 && hoverIdx !== i
              }"
              :style="linkStyle(i)"
              @mouseenter="hoverIdx = i"
              @click="onLinkClick($event, item, i)"
              @pointerdown="onLinkPointerDown($event, i)"
            >
              <!-- 网页截图 -->
              <div class="screenshot">
                <img
                  :src="screenshotOf(item)"
                  loading="lazy"
                  @error="onImgError($event, item)"
                  @load="onImgLoad($event, item)"
                />
                <div v-if="!imgLoaded[item.id!]" class="shot-placeholder">
                  <el-icon><Picture /></el-icon>
                </div>
              </div>
              <!-- 信息区 -->
              <div class="info">
                <div class="name">{{ item.name }}</div>
                <div class="url">{{ item.value }}</div>
                <div class="tags">
                  <span
                    v-if="item.cdsVersion"
                    class="tag cds"
                    :class="cdsClass(item.cdsVersion)"
                  >CDS {{ item.cdsVersion }}</span>
                  <span
                    v-if="item.env"
                    class="tag env"
                    :class="envClass(item.env as string)"
                  >{{ envLabel(item.env as string) }}</span>
                  <span
                    v-if="item.description"
                    class="tag desc"
                  >{{ item.description }}</span>
                </div>
              </div>
              <!-- 卡片操作 -->
              <div class="card-actions" @click.stop @pointerdown.stop>
                <el-icon class="op" @click="openEdit(item)"><Edit /></el-icon>
                <el-icon class="op danger" @click="onDelete(item)"><Delete /></el-icon>
              </div>
            </div>

            <!-- 详情展开覆盖层（拖拽抽出 ≥ 阈值时触发） -->
            <transition name="detail">
              <div
                v-if="detailIdx >= 0 && links[detailIdx]"
                class="detail-overlay"
                @click.self="closeDetail"
              >
                <div class="ld-card detail-card">
                  <button class="close-btn" @click="closeDetail">
                    <el-icon><Close /></el-icon>
                  </button>
                  <div class="detail-shot">
                    <img :src="screenshotOf(links[detailIdx])" @error="onImgError($event, links[detailIdx])" />
                  </div>
                  <div class="detail-info">
                    <div class="tags">
                      <span v-if="links[detailIdx].cdsVersion" class="tag cds" :class="cdsClass(links[detailIdx].cdsVersion)">CDS {{ links[detailIdx].cdsVersion }}</span>
                      <span v-if="links[detailIdx].env" class="tag env" :class="envClass(links[detailIdx].env as string)">{{ envLabel(links[detailIdx].env as string) }}</span>
                      <span v-if="links[detailIdx].description" class="tag desc">{{ links[detailIdx].description }}</span>
                    </div>
                    <h2>{{ links[detailIdx].name }}</h2>
                    <a :href="links[detailIdx].value" target="_blank" rel="noopener" class="detail-url">
                      <el-icon><Link /></el-icon>
                      <span>{{ links[detailIdx].value }}</span>
                    </a>
                    <div class="detail-actions">
                      <button class="ld-btn ld-btn-primary" @click="openLink(links[detailIdx])">
                        <el-icon><Link /></el-icon> 访问链接
                      </button>
                      <button class="ld-btn ld-btn-ghost" @click="openEdit(links[detailIdx])">
                        <el-icon><Edit /></el-icon> 编辑
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </transition>
          </div>
        </div>
      </div>

      <!-- ========== 笔记：3D 立体堆叠（简单标题卡） ========== -->
      <div class="ld-card pane">
        <div class="pane-head">
          <div class="pane-head-left">
            <span class="pane-title">笔记</span>
            <span class="pane-count">{{ notes.length }} 项</span>
          </div>
          <div class="pane-tip">拖拽顶部卡片抽出展开详情</div>
        </div>
        <div class="pane-body">
          <div v-if="notes.length === 0" class="empty">暂无笔记</div>
          <div v-else class="card-stack note-stack" @mouseleave="hoverNoteIdx = -1">
            <div
              v-for="(item, i) in notes"
              :key="item.id"
              class="stack-card note-card"
              :class="{
                top: i === 0,
                hovered: hoverNoteIdx === i,
                dragging: dragNoteIdx === i,
                dim: hoverNoteIdx >= 0 && hoverNoteIdx !== i
              }"
              :style="noteStyle(i)"
              @mouseenter="hoverNoteIdx = i"
              @click="onNoteClick($event, item, i)"
              @pointerdown="onNotePointerDown($event, i)"
            >
              <div class="note-head">
                <el-icon class="note-icon"><Document /></el-icon>
                <div class="note-title">{{ item.name }}</div>
              </div>
              <div class="note-meta">
                <span v-if="item.description" class="note-tag">{{ item.description }}</span>
                <span class="note-time">{{ formatTime(item.updateTime || item.createTime) }}</span>
              </div>
              <div class="card-actions" @click.stop @pointerdown.stop>
                <el-icon class="op" @click="openEdit(item)"><Edit /></el-icon>
                <el-icon class="op danger" @click="onDelete(item)"><Delete /></el-icon>
              </div>
            </div>

            <transition name="detail">
              <div
                v-if="detailNoteIdx >= 0 && notes[detailNoteIdx]"
                class="detail-overlay"
                @click.self="closeNoteDetail"
              >
                <div class="ld-card detail-card note-detail">
                  <button class="close-btn" @click="closeNoteDetail">
                    <el-icon><Close /></el-icon>
                  </button>
                  <div class="detail-info">
                    <h2>{{ notes[detailNoteIdx].name }}</h2>
                    <div class="note-detail-meta">
                      <span v-if="notes[detailNoteIdx].description" class="note-tag">{{ notes[detailNoteIdx].description }}</span>
                      <span class="note-time">{{ formatTime(notes[detailNoteIdx].updateTime || notes[detailNoteIdx].createTime) }}</span>
                    </div>
                    <pre class="note-content">{{ notes[detailNoteIdx].value }}</pre>
                    <div class="detail-actions">
                      <button class="ld-btn ld-btn-ghost" @click="openEdit(notes[detailNoteIdx])">
                        <el-icon><Edit /></el-icon> 编辑
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </transition>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <div v-if="dialogVisible" class="modal-overlay" @click.self="dialogVisible = false">
      <div class="modal ld-card">
        <div class="modal-title">{{ form.id ? "编辑" : "新建" }}{{ form.type === "link" ? "收藏" : "笔记" }}</div>
        <div class="modal-row">
          <label>名称</label>
          <el-input v-model="form.name" placeholder="收藏网站名 / 笔记标题" maxlength="128" />
        </div>
        <div class="modal-row">
          <label>{{ form.type === "link" ? "URL" : "内容" }}</label>
          <el-input
            v-model="form.value"
            :type="form.type === 'note' ? 'textarea' : 'text'"
            :rows="form.type === 'note' ? 5 : undefined"
            :placeholder="form.type === 'note' ? '笔记正文（支持多行）' : 'https://...'"
          />
        </div>
        <div class="modal-row">
          <label>备注</label>
          <el-input v-model="form.description" placeholder="分类/标签，可选" maxlength="32" />
        </div>
        <!-- 收藏专属：环境 / CDS 版本 / 截图 URL -->
        <template v-if="form.type === 'link'">
          <div class="modal-row modal-row-2col">
            <div class="col">
              <label>环境</label>
              <el-select v-model="form.env" placeholder="选择环境（可选）" clearable>
                <el-option label="测试" value="test" />
                <el-option label="集成" value="integration" />
                <el-option label="生产" value="prod" />
              </el-select>
            </div>
            <div class="col">
              <label>CDS 版本</label>
              <el-select v-model="form.cdsVersion" placeholder="选择版本（可选）" clearable>
                <el-option label="CDS 3.0" value="3.0" />
                <el-option label="CDS 5.0" value="5.0" />
              </el-select>
            </div>
          </div>
          <div class="modal-row">
            <label>截图 URL（留空自动从公开截图服务生成）</label>
            <el-input
              v-model="form.screenshot"
              placeholder="如 https://...png，留空自动用 mShots 服务"
              clearable
            />
          </div>
        </template>
        <div class="modal-footer">
          <button class="ld-btn ld-btn-ghost" @click="dialogVisible = false">取消</button>
          <button class="ld-btn ld-btn-primary" :disabled="submitting" @click="onSubmit">
            {{ submitting ? "保存中…" : "保存" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Search, Plus, Link, Document, Edit, Delete,
  ArrowUp, ArrowDown, Close, Picture
} from "@element-plus/icons-vue";
import {
  fetchFavorites,
  createFavorite,
  updateFavorite,
  deleteFavorite,
  type Favorite
} from "@/api/bookmark";

defineOptions({ name: "BookmarkView" });

// ===== 数据 =====
const keyword = ref("");
const links = ref<Favorite[]>([]);
const notes = ref<Favorite[]>([]);

// ===== 收藏：3D 堆叠交互状态 =====
const hoverIdx = ref(-1);
const dragIdx = ref(-1);
const dragStartX = ref(0);
const dragDeltaX = ref(0);
const dragging = ref(false);
const detailIdx = ref(-1);
const imgLoaded = ref<Record<number, boolean>>({});

// ===== 笔记：3D 堆叠交互状态 =====
const hoverNoteIdx = ref(-1);
const dragNoteIdx = ref(-1);
const dragNoteStartX = ref(0);
const dragNoteDeltaX = ref(0);
const draggingNote = ref(false);
const detailNoteIdx = ref(-1);

// ===== 编辑弹窗 =====
const dialogVisible = ref(false);
const submitting = ref(false);
const form = ref<Favorite>({ name: "", value: "", type: "link", description: "" });

let searchTimer: any = null;
// 阈值：拖拽超过该距离则展开详情，否则弹回
const DRAG_THRESHOLD = 90;

async function load() {
  try {
    const res = await fetchFavorites({ keyword: keyword.value || undefined });
    const all: Favorite[] = (res as any).data || [];
    // 按 updateTime desc 排序，最新的在顶部（index 0）
    const sorted = [...all].sort((a, b) =>
      (b.updateTime || b.createTime || "").localeCompare(a.updateTime || a.createTime || "")
    );
    links.value = sorted.filter((x) => x.type === "link");
    notes.value = sorted.filter((x) => x.type === "note");
    // 重置交互状态
    hoverIdx.value = -1;
    dragIdx.value = -1;
    detailIdx.value = -1;
    hoverNoteIdx.value = -1;
    dragNoteIdx.value = -1;
    detailNoteIdx.value = -1;
    imgLoaded.value = {};
  } catch {
    /* 拦截器已提示 */
  }
}

function onSearch() {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(load, 250);
}

// ===== 截图 URL 兜底：用户没填时用 mShots 公开截图服务 =====
function screenshotOf(item: Favorite): string {
  if (item.screenshot) return item.screenshot;
  if (!item.value) return "";
  return `https://s.wordpress.com/mshots/v1/${encodeURIComponent(item.value)}?w=480&h=320`;
}

function onImgError(e: Event, item: Favorite) {
  // 加载失败 → 用 favicon 兜底
  const img = e.target as HTMLImageElement;
  if (!item.value) return;
  try {
    const u = new URL(item.value);
    img.src = `https://www.google.com/s2/favicons?domain=${u.hostname}&sz=128`;
  } catch {
    img.src = "";
  }
}

function onImgLoad(e: Event, item: Favorite) {
  if (item.id) imgLoaded.value[item.id] = true;
}

// ===== 标签样式映射 =====
function cdsClass(v: string): string {
  // CDS 3.0 → 暖色橙红（旧版警示）；5.0 → 冷色蓝青（新版）便于一眼区分
  if (v === "3.0") return "cds-3";
  if (v === "5.0") return "cds-5";
  return "cds-unknown";
}

function envClass(env: string): string {
  return `env-${env}`;
}

function envLabel(env: string): string {
  const map: Record<string, string> = { test: "测试", integration: "集成", prod: "生产" };
  return map[env] || env;
}

// ===== 立体堆叠 transform 计算 =====
function linkStyle(i: number) {
  const n = links.value.length;
  // 基础：index 越大（越靠后）越下沉 + 缩小 + 旋转
  const baseY = i * 22;
  const baseZ = -i * 16;
  const baseScale = Math.max(0.78, 1 - i * 0.04);
  const baseRot = (i % 2 === 0 ? 1 : -1) * (1 + i * 0.7);

  let y = baseY, z = baseZ, scale = baseScale, rot = baseRot, x = 0;
  let zIdx = n - i; // 默认按 index 反序，前面的覆盖后面的

  // hover 弹出
  if (hoverIdx.value === i) {
    y = baseY - 8;
    z = 80 - i * 6;
    scale = 1.1;
    rot = 0;
    zIdx = 1000;
  } else if (hoverIdx.value >= 0) {
    // 其他卡：下沉 + 旋转加剧（旋钮转动下沉）
    const dist = Math.abs(hoverIdx.value - i);
    y = baseY + dist * 14 + 6;
    z = baseZ - dist * 10;
    scale = Math.max(0.7, baseScale - dist * 0.03);
    rot = baseRot + (i < hoverIdx.value ? -1 : 1) * dist * 3;
  }

  // 拖拽中
  if (dragging.value && dragIdx.value === i) {
    x = dragDeltaX.value;
    z = 120;
    scale = 1.12;
    y = baseY - 12;
    rot = dragDeltaX.value * 0.18;
    zIdx = 2000;
  } else if (dragging.value && dragIdx.value >= 0) {
    // 后方卡按重量（i+1）依次下沉
    const weight = (i + 1) * 0.35;
    const dragAbs = Math.abs(dragDeltaX.value);
    y = baseY + dragAbs * weight * 0.35;
    z = baseZ - dragAbs * weight * 0.12;
    scale = Math.max(0.7, baseScale - dragAbs * weight * 0.0006);
  }

  const transitionMode =
    (dragging.value && dragIdx.value === i)
      ? "none" // 拖拽中不使用 transition，避免跟不上指针
      : "transform 0.55s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.3s";

  return {
    transform: `translate3d(${x}px, ${y}px, ${z}px) scale(${scale}) rotateY(${rot}deg)`,
    zIndex: zIdx,
    transition: transitionMode
  };
}

function noteStyle(i: number) {
  const n = notes.value.length;
  const baseY = i * 18;
  const baseZ = -i * 14;
  const baseScale = Math.max(0.82, 1 - i * 0.035);
  const baseRot = (i % 2 === 0 ? 1 : -1) * (0.8 + i * 0.6);

  let y = baseY, z = baseZ, scale = baseScale, rot = baseRot, x = 0;
  let zIdx = n - i;

  if (hoverNoteIdx.value === i) {
    y = baseY - 6;
    z = 80 - i * 6;
    scale = 1.08;
    rot = 0;
    zIdx = 1000;
  } else if (hoverNoteIdx.value >= 0) {
    const dist = Math.abs(hoverNoteIdx.value - i);
    y = baseY + dist * 12 + 4;
    z = baseZ - dist * 8;
    scale = Math.max(0.72, baseScale - dist * 0.025);
    rot = baseRot + (i < hoverNoteIdx.value ? -1 : 1) * dist * 2.5;
  }

  if (draggingNote.value && dragNoteIdx.value === i) {
    x = dragNoteDeltaX.value;
    z = 120;
    scale = 1.1;
    y = baseY - 10;
    rot = dragNoteDeltaX.value * 0.18;
    zIdx = 2000;
  } else if (draggingNote.value && dragNoteIdx.value >= 0) {
    const weight = (i + 1) * 0.35;
    const dragAbs = Math.abs(dragNoteDeltaX.value);
    y = baseY + dragAbs * weight * 0.35;
    z = baseZ - dragAbs * weight * 0.12;
    scale = Math.max(0.72, baseScale - dragAbs * weight * 0.0006);
  }

  const transitionMode =
    (draggingNote.value && dragNoteIdx.value === i)
      ? "none"
      : "transform 0.55s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.3s";

  return {
    transform: `translate3d(${x}px, ${y}px, ${z}px) scale(${scale}) rotateY(${rot}deg)`,
    zIndex: zIdx,
    transition: transitionMode
  };
}

// ===== 收藏：点击 & 拖拽处理 =====
function onLinkClick(e: MouseEvent, item: Favorite, i: number) {
  // 拖拽中或刚结束拖拽的点击忽略
  if (dragging.value || Math.abs(dragDeltaX.value) > 5) return;
  // 详情展开时点击不触发访问
  if (detailIdx.value >= 0) return;
  // 只有顶部卡（i=0）或 hover 中的卡点击直接访问
  if (hoverIdx.value === i || i === 0) {
    openLink(item);
  }
}

function onLinkPointerDown(e: PointerEvent, i: number) {
  // 只有顶部卡可拖拽抽出
  if (i !== 0) return;
  if (detailIdx.value >= 0) return;
  dragIdx.value = i;
  dragStartX.value = e.clientX;
  dragDeltaX.value = 0;
  dragging.value = true;
  // 捕获指针，确保 pointermove/up 都能收到
  (e.target as Element).setPointerCapture?.(e.pointerId);
  window.addEventListener("pointermove", onLinkPointerMove);
  window.addEventListener("pointerup", onLinkPointerUp);
}

function onLinkPointerMove(e: PointerEvent) {
  if (!dragging.value) return;
  dragDeltaX.value = e.clientX - dragStartX.value;
}

function onLinkPointerUp() {
  window.removeEventListener("pointermove", onLinkPointerMove);
  window.removeEventListener("pointerup", onLinkPointerUp);
  if (!dragging.value) return;
  dragging.value = false;
  const dist = Math.abs(dragDeltaX.value);
  if (dist >= DRAG_THRESHOLD) {
    // 展开详情
    detailIdx.value = dragIdx.value;
  }
  // 弹簧回弹：清空 dragDeltaX，触发 cubic-bezier(0.34, 1.56, 0.64, 1) 过渡
  // 用 setTimeout 让 transition 重新激活（dragIdx 重置后 transition 生效）
  const idx = dragIdx.value;
  dragIdx.value = -1;
  setTimeout(() => {
    dragDeltaX.value = 0;
  }, 0);
  // detailIdx 已设则保留
  if (detailIdx.value < 0) {
    // 未达阈值，记录一次"刚拖过"防 click
    setTimeout(() => {
      // no-op, click handler 已检查
    }, 0);
  }
}

function closeDetail() {
  detailIdx.value = -1;
}

// ===== 笔记：点击 & 拖拽处理 =====
function onNoteClick(e: MouseEvent, item: Favorite, i: number) {
  if (draggingNote.value || Math.abs(dragNoteDeltaX.value) > 5) return;
  if (detailNoteIdx.value >= 0) return;
  if (hoverNoteIdx.value === i || i === 0) {
    // 笔记点击顶部卡也展开详情
    detailNoteIdx.value = i;
  }
}

function onNotePointerDown(e: PointerEvent, i: number) {
  if (i !== 0) return;
  if (detailNoteIdx.value >= 0) return;
  dragNoteIdx.value = i;
  dragNoteStartX.value = e.clientX;
  dragNoteDeltaX.value = 0;
  draggingNote.value = true;
  (e.target as Element).setPointerCapture?.(e.pointerId);
  window.addEventListener("pointermove", onNotePointerMove);
  window.addEventListener("pointerup", onNotePointerUp);
}

function onNotePointerMove(e: PointerEvent) {
  if (!draggingNote.value) return;
  dragNoteDeltaX.value = e.clientX - dragNoteStartX.value;
}

function onNotePointerUp() {
  window.removeEventListener("pointermove", onNotePointerMove);
  window.removeEventListener("pointerup", onNotePointerUp);
  if (!draggingNote.value) return;
  draggingNote.value = false;
  const dist = Math.abs(dragNoteDeltaX.value);
  if (dist >= DRAG_THRESHOLD) {
    detailNoteIdx.value = dragNoteIdx.value;
  }
  dragNoteIdx.value = -1;
  setTimeout(() => {
    dragNoteDeltaX.value = 0;
  }, 0);
}

function closeNoteDetail() {
  detailNoteIdx.value = -1;
}

// ===== 通用操作 =====
function openLink(item: Favorite) {
  if (item.value) window.open(item.value, "_blank");
}

function openCreate(type: "link" | "note") {
  form.value = { name: "", value: "", type, description: "", env: undefined, cdsVersion: undefined, screenshot: "" };
  dialogVisible.value = true;
}

function openEdit(item: Favorite) {
  form.value = { ...item };
  dialogVisible.value = true;
}

async function onSubmit() {
  if (!form.value.name.trim()) {
    ElMessage.warning("请填写名称");
    return;
  }
  if (!form.value.value.trim()) {
    ElMessage.warning("请填写" + (form.value.type === "link" ? " URL" : "内容"));
    return;
  }
  submitting.value = true;
  try {
    if (form.value.id) {
      await updateFavorite(form.value.id, { ...form.value });
      ElMessage.success("已保存");
    } else {
      await createFavorite({ ...form.value });
      ElMessage.success("已创建");
    }
    dialogVisible.value = false;
    await load();
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false;
  }
}

async function onDelete(item: Favorite) {
  try {
    await ElMessageBox.confirm(`确定删除「${item.name}」？`, "提示", { type: "warning" });
    await deleteFavorite(item.id!);
    ElMessage.success("已删除");
    await load();
  } catch {
    /* 取消或拦截器已提示 */
  }
}

function formatTime(t?: string) {
  if (!t) return "";
  return (t as string).replace("T", " ").substring(5, 16);
}

onMounted(load);

// 组件卸载前清理全局监听
onBeforeUnmount(() => {
  window.removeEventListener("pointermove", onLinkPointerMove);
  window.removeEventListener("pointerup", onLinkPointerUp);
  window.removeEventListener("pointermove", onNotePointerMove);
  window.removeEventListener("pointerup", onNotePointerUp);
});
</script>

<style scoped lang="scss">
.bookmark-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
}
.search {
  flex: 1;
  max-width: 520px;
}
.top-actions {
  margin-left: auto;
  display: flex;
  gap: 10px;
}

.dual-pane {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  min-height: calc(100vh - 200px);
}
.pane {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}
.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px 10px;
  border-bottom: 1px solid $border-glass;
}
.pane-head-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.pane-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}
.pane-count {
  font-size: 12px;
  color: $text-tertiary;
}
.pane-tip {
  font-size: 11px;
  color: $text-tertiary;
  opacity: 0.85;
}
.pane-body {
  flex: 1;
  overflow: hidden;
  padding: 18px 18px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
}
.empty {
  text-align: center;
  color: $text-tertiary;
  padding: 60px 0;
  font-size: 13px;
}

/* ========== 3D 立体堆叠容器 ========== */
.card-stack {
  position: relative;
  width: 100%;
  max-width: 420px;
  height: 380px;
  perspective: 1400px;
  perspective-origin: 50% 60%;
  transform-style: preserve-3d;
}
.note-stack {
  height: 320px;
}

/* ========== 堆叠卡片（共享样式） ========== */
.stack-card {
  position: absolute;
  top: 0;
  left: 50%;
  margin-left: -180px;  /* 卡宽 360 / 2 */
  width: 360px;
  border-radius: 18px;
  backface-visibility: hidden;
  cursor: pointer;
  user-select: none;
  /* 玻璃风：半透明 + 内高光 */
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.03));
  border: 1px solid $border-glass;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.12);
  overflow: hidden;
  will-change: transform;
}
.stack-card.dim {
  filter: brightness(0.55) saturate(0.7);
}
.stack-card.dragging {
  cursor: grabbing;
}

/* ========== 收藏卡片 ========== */
.link-card {
  height: 320px;
  display: flex;
  flex-direction: column;
}
.screenshot {
  position: relative;
  width: 100%;
  height: 180px;
  background: rgba(0, 0, 0, 0.25);
  overflow: hidden;
  border-bottom: 1px solid $border-subtle;
}
.screenshot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top;
  display: block;
}
.shot-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-tertiary;
  font-size: 32px;
  background: linear-gradient(135deg, rgba(167, 139, 250, 0.08), rgba(196, 181, 253, 0.04));
}
.info {
  flex: 1;
  padding: 12px 16px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.name {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.url {
  font-size: 11px;
  color: $accent-light;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}
.tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 8px;
  font-weight: 600;
  letter-spacing: 0.3px;
  white-space: nowrap;
}
/* CDS 版本：3.0 暖橙红 / 5.0 冷蓝青 —— 一眼区分避免选错 */
.cds.cds-3 {
  background: linear-gradient(135deg, #f97316, #ef4444);
  color: #fff;
  box-shadow: 0 0 8px rgba(239, 68, 68, 0.4);
}
.cds.cds-5 {
  background: linear-gradient(135deg, #06b6d4, #3b82f6);
  color: #fff;
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.4);
}
.cds.cds-unknown {
  background: rgba(107, 114, 128, 0.5);
  color: $text-primary;
}
/* 环境标识：测试青绿 / 集成橙黄 / 生产红 */
.env.env-test {
  background: rgba(45, 212, 191, 0.18);
  color: #5eead4;
  border: 1px solid rgba(45, 212, 191, 0.4);
}
.env.env-integration {
  background: rgba(251, 191, 36, 0.18);
  color: #fcd34d;
  border: 1px solid rgba(251, 191, 36, 0.4);
}
.env.env-prod {
  background: rgba(248, 113, 113, 0.22);
  color: #fca5a5;
  border: 1px solid rgba(248, 113, 113, 0.5);
  font-weight: 700;
}
.tag.desc {
  background: rgba(167, 139, 250, 0.12);
  color: $text-secondary;
  border: 1px solid rgba(167, 139, 250, 0.18);
}

.card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.18s;
  pointer-events: none;
  z-index: 5;
}
.stack-card:hover .card-actions,
.stack-card.hovered .card-actions {
  opacity: 1;
  pointer-events: auto;
}
.op {
  cursor: pointer;
  color: $text-primary;
  font-size: 14px;
  background: rgba(0, 0, 0, 0.45);
  padding: 5px;
  border-radius: 6px;
  border: 1px solid $border-glass;
  backdrop-filter: blur(4px);
  &:hover {
    color: $accent-light;
    border-color: $border-accent;
  }
  &.danger:hover {
    color: #f87171;
    border-color: rgba(248, 113, 113, 0.5);
  }
}

/* ========== 笔记卡片 ========== */
.note-card {
  height: 140px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.note-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.note-icon {
  color: $accent-light;
  font-size: 16px;
}
.note-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.note-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.note-tag {
  font-size: 10px;
  color: $text-tertiary;
  background: rgba(167, 139, 250, 0.08);
  padding: 2px 8px;
  border-radius: 8px;
}
.note-time {
  font-size: 11px;
  color: $text-tertiary;
}

/* ========== 详情展开覆盖层 ========== */
.detail-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(8px);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}
.detail-card {
  position: relative;
  width: min(720px, 100%);
  max-height: 86vh;
  overflow: auto;
  padding: 28px 32px;
  display: flex;
  gap: 24px;
  background: rgba(20, 14, 38, 0.92);
  border: 1px solid $border-accent;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45), 0 0 0 1px rgba(196, 181, 253, 0.18);
}
.close-btn {
  position: absolute;
  top: 14px;
  right: 14px;
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid $border-glass;
  border-radius: 8px;
  width: 30px;
  height: 30px;
  cursor: pointer;
  color: $text-secondary;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 5;
  transition: all 0.15s;
  &:hover {
    color: $text-primary;
    border-color: $border-accent;
  }
}
.detail-shot {
  flex: 0 0 320px;
  height: 220px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid $border-subtle;
  background: rgba(0, 0, 0, 0.3);
}
.detail-shot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top;
}
.detail-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.detail-info h2 {
  font-size: 22px;
  font-weight: 700;
  color: $text-primary;
  margin: 6px 0 4px;
}
.detail-url {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: $accent-light;
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  word-break: break-all;
  text-decoration: none;
  &:hover { text-decoration: underline; }
}
.detail-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}
.note-detail {
  flex-direction: column;
  gap: 12px;
}
.note-detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}
.note-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: $text-secondary;
  line-height: 1.7;
  background: rgba(0, 0, 0, 0.25);
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid $border-subtle;
  max-height: 60vh;
  overflow: auto;
}

/* ========== 详情展开过渡 ========== */
.detail-enter-active,
.detail-leave-active {
  transition: opacity 0.28s ease;
  .detail-card {
    transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.28s ease;
  }
}
.detail-enter-from,
.detail-leave-to {
  opacity: 0;
  .detail-card {
    transform: scale(0.85) translateY(20px);
    opacity: 0;
  }
}

/* ========== 编辑弹窗 ========== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.modal {
  width: 480px;
  max-width: 100%;
  padding: 22px 24px;
  background: $bg-card;
}
.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 18px;
}
.modal-row {
  margin-bottom: 14px;
  label {
    display: block;
    font-size: 12px;
    color: $text-tertiary;
    margin-bottom: 6px;
  }
}
.modal-row-2col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  .col {
    display: flex;
    flex-direction: column;
  }
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
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
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}
.ld-btn-primary {
  background: linear-gradient(135deg, $accent, $accent-light);
  color: #fff;
  border: none;
  &:hover:not(:disabled) {
    box-shadow: 0 4px 14px rgba(167, 139, 250, 0.35);
    color: #fff;
  }
}
</style>
