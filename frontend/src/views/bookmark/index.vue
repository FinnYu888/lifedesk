<template>
  <div class="bookmark-page">
    <!-- 顶部搜索（新建按钮已挪到各自区域 pane-head） -->
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
    </div>

    <!-- 双栏：左=收藏，右=笔记 -->
    <div class="dual-pane">
      <!-- ========== 收藏：3D 左右立体轮播（cover flow） ========== -->
      <div class="ld-card pane">
        <div class="pane-head">
          <div class="pane-head-left">
            <span class="pane-title">收藏</span>
            <span class="pane-count">{{ links.length }} 项</span>
          </div>
          <div class="pane-head-right">
            <span class="pane-tip">悬停弹起 · 点箭头切换</span>
            <button class="ld-btn ld-btn-ghost pane-create" @click="openCreate('link')">
              <el-icon><Plus /></el-icon> 新建收藏
            </button>
          </div>
        </div>
        <div class="pane-body">
          <div v-if="links.length === 0" class="empty">暂无收藏，点击右上「新建收藏」</div>
          <div
            v-else
            class="card-stack"
            @mouseenter="onLinkStackEnter"
            @mouseleave="onLinkStackLeave"
          >
            <!-- 左箭头：.arrow-zone 父容器做热区（80x80），::after 画中心 44x44 圆形视觉，z-index 9999 远高于卡片，杜绝连续点击穿透 -->
            <div
              v-if="links.length > 1"
              class="arrow-zone arrow-zone-left"
              @click="linkPrev"
              aria-label="上一张"
            >
              <el-icon><ArrowLeft /></el-icon>
            </div>

            <!-- cover flow 卡片层 -->
            <div
              v-for="(item, i) in links"
              :key="item.id"
              class="stack-card link-card"
              :class="{
                center: i === currentLinkIdx,
                hovered: hoverIdx === i,
                dragging: dragIdx === i,
                dim: hoverIdx >= 0 && hoverIdx !== i
              }"
              :style="linkStyle(i)"
              @mouseenter="hoverIdx = i"
              @click="onLinkClick($event, item, i)"
              @pointerdown="onLinkPointerDown($event, i)"
            >
              <!-- 主题色横幅：取自桌面 8 张微信图片的主题色，按卡片序号循环 -->
              <div class="theme-banner" :style="themeStyle(i)">
                <span class="banner-letter">{{ (item.name || "?").charAt(0).toUpperCase() }}</span>
              </div>
              <!-- 信息区：填充背景色让文字更突出 -->
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
                <el-icon class="op op-edit" title="编辑" @click="openEdit(item)"><Edit /></el-icon>
                <el-icon class="op op-del" title="删除" @click="onDelete(item)"><Delete /></el-icon>
              </div>
            </div>

            <!-- 右箭头 -->
            <div
              v-if="links.length > 1"
              class="arrow-zone arrow-zone-right"
              @click="linkNext"
              aria-label="下一张"
            >
              <el-icon><ArrowRight /></el-icon>
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
                  <div class="detail-shot" :style="themeStyle(detailIdx)">
                    <span class="banner-letter detail-letter">{{ (links[detailIdx].name || "?").charAt(0).toUpperCase() }}</span>
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

      <!-- ========== 笔记：3D 左右立体轮播（简单标题卡） ========== -->
      <div class="ld-card pane">
        <div class="pane-head">
          <div class="pane-head-left">
            <span class="pane-title">笔记</span>
            <span class="pane-count">{{ notes.length }} 项</span>
          </div>
          <div class="pane-head-right">
            <span class="pane-tip">悬停弹起 · 点箭头切换</span>
            <button class="ld-btn ld-btn-ghost pane-create" @click="openCreate('note')">
              <el-icon><Plus /></el-icon> 新建笔记
            </button>
          </div>
        </div>
        <div class="pane-body">
          <div v-if="notes.length === 0" class="empty">暂无笔记，点击右上「新建笔记」</div>
          <div
            v-else
            class="card-stack note-stack"
            @mouseenter="onNoteStackEnter"
            @mouseleave="onNoteStackLeave"
          >
            <!-- 左箭头 -->
            <div
              v-if="notes.length > 1"
              class="arrow-zone arrow-zone-left"
              @click="notePrev"
              aria-label="上一篇"
            >
              <el-icon><ArrowLeft /></el-icon>
            </div>

            <div
              v-for="(item, i) in notes"
              :key="item.id"
              class="stack-card note-card"
              :class="{
                center: i === currentNoteIdx,
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
                <el-icon class="op op-edit" title="编辑" @click="openEdit(item)"><Edit /></el-icon>
                <el-icon class="op op-del" title="删除" @click="onDelete(item)"><Delete /></el-icon>
              </div>
            </div>

            <!-- 右箭头 -->
            <div
              v-if="notes.length > 1"
              class="arrow-zone arrow-zone-right"
              @click="noteNext"
              aria-label="下一篇"
            >
              <el-icon><ArrowRight /></el-icon>
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
                    <!-- 每行后带复制按钮，点击复制该行文本 -->
                    <div class="note-content">
                      <div
                        v-for="(line, li) in activeNoteLines"
                        :key="li"
                        class="note-line"
                      >
                        <span class="line-text">{{ line }}</span>
                        <button
                          class="line-copy"
                          title="复制该行"
                          @click="copyNoteLine(line)"
                        >
                          <el-icon><CopyDocument /></el-icon>
                        </button>
                      </div>
                      <div v-if="!activeNoteLines.length" class="note-line empty-line">（笔记内容为空）</div>
                    </div>
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
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Search, Plus, Link, Document, Edit, Delete,
  ArrowUp, ArrowDown, ArrowLeft, ArrowRight, Close, CopyDocument
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

// ===== 收藏：3D 轮播交互状态 =====
const hoverIdx = ref(-1);
const dragIdx = ref(-1);
const dragStartX = ref(0);
const dragDeltaX = ref(0);          // 拖拽+惯性过程中的连续像素位置（往右为正）
const dragging = ref(false);
const detailIdx = ref(-1);
// cover flow 中心卡索引
const currentLinkIdx = ref(0);
let autoLinkTimer: any = null;
const AUTO_INTERVAL = 3500;
// 惯性滑动状态：true 时 linkStyle 用 dragDeltaX 计算 offset，transition: none
const inertiaLinkRunning = ref(false);
// 拖拽速度历史（最近几帧的 {t, x}），用于松手时算速度
let linkDragHistory: { t: number; x: number }[] = [];
let linkInertiaRAF: number | null = null;

// ===== 笔记：3D 轮播交互状态 =====
const hoverNoteIdx = ref(-1);
const dragNoteIdx = ref(-1);
const dragNoteStartX = ref(0);
const dragNoteDeltaX = ref(0);
const draggingNote = ref(false);
const detailNoteIdx = ref(-1);
// 笔记详情：按行拆分（供每行复制按钮使用）
const activeNoteLines = computed<string[]>(() => {
  const note = detailNoteIdx.value >= 0 ? notes.value[detailNoteIdx.value] : null;
  if (!note?.value) return [];
  return note.value.split("\n");
});

// 复制笔记某一行文本
async function copyNoteLine(line: string) {
  try {
    await navigator.clipboard.writeText(line);
    ElMessage.success("已复制该行");
  } catch {
    // 剪贴板 API 不可用时的兜底（非 https/无权限）
    const ta = document.createElement("textarea");
    ta.value = line;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    try {
      document.execCommand("copy");
      ElMessage.success("已复制该行");
    } catch {
      ElMessage.error("复制失败，请手动复制");
    }
    document.body.removeChild(ta);
  }
}
const currentNoteIdx = ref(0);
let autoNoteTimer: any = null;
const inertiaNoteRunning = ref(false);
let noteDragHistory: { t: number; x: number }[] = [];
let noteInertiaRAF: number | null = null;

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
    currentLinkIdx.value = 0;
    currentNoteIdx.value = 0;
  } catch {
    /* 拦截器已提示 */
  }
}

function onSearch() {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(load, 250);
}

// ===== 主题色横幅：取自桌面 8 张微信图片的主题色，按卡片序号循环分配 =====
const THEME_COLORS = [
  "#a71e36", // 红莓
  "#ab6e2e", // 琥珀
  "#3c608a", // 海蓝
  "#5a6e33", // 橄榄绿
  "#43929a", // 青
  "#a48a34", // 金黄
  "#a3607c", // 玫瑰
  "#623b5e"  // 紫红
];

function themeStyle(i: number): Record<string, string> {
  const c = THEME_COLORS[((i % THEME_COLORS.length) + THEME_COLORS.length) % THEME_COLORS.length];
  return {
    background: `linear-gradient(135deg, ${c} 0%, ${c}cc 55%, ${c}88 100%)`,
    boxShadow: `inset 0 1px 0 rgba(255,255,255,0.18), 0 6px 18px ${c}55`
  };
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

// ===== 立体轮播（cover flow）transform 计算 =====
// offset = i - currentIdx（相对中心的偏移，左侧为负、右侧为正）
// 卡片按 offset 做 translateX + rotateY + translateZ + scale，形成左右立体轮播
// 关键：规整 offset 到 [-n/2, n/2] 范围，实现首位相连循环
// （最后一张往后切换时，第一张从右侧远处平滑滑入中心，而非全员大跳变）
function normalizeOffset(rawOffset: number, n: number): number {
  if (n <= 0) return 0;
  // 把 offset 规整到 [-n/2, n/2) 范围（半开区间，避免 n/2 与 -n/2 重合）
  let o = rawOffset;
  while (o > n / 2) o -= n;
  while (o <= -n / 2) o += n;
  return o;
}

function linkStyle(i: number) {
  const n = links.value.length;
  // 拖拽/惯性过程中的连续偏移（dragDeltaX 单位：像素；正值=往右拖）
  const dragOffsetCards =
    dragging.value || inertiaLinkRunning.value ? dragDeltaX.value / 260 : 0;
  const rawOffset = i - currentLinkIdx.value - dragOffsetCards;
  // 规整到 [-n/2, n/2]，实现循环（最后一张→第一张平滑过渡）
  const offset = normalizeOffset(rawOffset, n);
  const abs = Math.abs(offset);
  // cover flow 排布：左右展开 + 侧面旋转 + 远处下沉（间距 +30%）
  let x = offset * 260;
  let y = 0;
  let z = -abs * 124;
  let scale = Math.max(0.62, 1 - abs * 0.13);
  let rot = offset * -36;
  let zIdx = 100 - Math.floor(abs);
  let opacity = Math.max(0.36, 1 - abs * 0.18);

  // 鼠标 hover 弹起：该卡大幅前移 + 放大
  if (hoverIdx.value === i && !dragging.value && !inertiaLinkRunning.value) {
    z = z + 90;
    scale = Math.max(scale, 1.12);
    y = -10;
    rot = rot * 0.25;
    zIdx = 1000;
  }

  const transitionMode =
    (dragging.value && dragIdx.value === i) || inertiaLinkRunning.value
      ? "none"
      : "transform 0.6s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.3s, opacity 0.4s";

  return {
    transform: `translate3d(${x}px, ${y}px, ${z}px) scale(${scale}) rotateY(${rot}deg)`,
    zIndex: zIdx,
    opacity: opacity,
    transition: transitionMode
  };
}

function noteStyle(i: number) {
  const n = notes.value.length;
  const dragOffsetCards =
    draggingNote.value || inertiaNoteRunning.value ? dragNoteDeltaX.value / 234 : 0;
  const rawOffset = i - currentNoteIdx.value - dragOffsetCards;
  const offset = normalizeOffset(rawOffset, n);
  const abs = Math.abs(offset);
  let x = offset * 234;
  let y = 0;
  let z = -abs * 114;
  let scale = Math.max(0.64, 1 - abs * 0.12);
  let rot = offset * -34;
  let zIdx = 100 - Math.floor(abs);
  let opacity = Math.max(0.38, 1 - abs * 0.16);

  if (hoverNoteIdx.value === i && !draggingNote.value && !inertiaNoteRunning.value) {
    z = z + 80;
    scale = Math.max(scale, 1.1);
    y = -8;
    rot = rot * 0.25;
    zIdx = 1000;
  }

  const transitionMode =
    (draggingNote.value && dragNoteIdx.value === i) || inertiaNoteRunning.value
      ? "none"
      : "transform 0.6s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.3s, opacity 0.4s";

  return {
    transform: `translate3d(${x}px, ${y}px, ${z}px) scale(${scale}) rotateY(${rot}deg)`,
    zIndex: zIdx,
    opacity: opacity,
    transition: transitionMode
  };
}

// ===== 自动轮播 + 鼠标进出容器 =====
function startLinkAuto() {
  stopLinkAuto();
  if (links.value.length < 2) return;
  autoLinkTimer = setInterval(() => {
    if (dragging.value || detailIdx.value >= 0) return;
    currentLinkIdx.value = (currentLinkIdx.value + 1) % links.value.length;
  }, AUTO_INTERVAL);
}
function stopLinkAuto() {
  if (autoLinkTimer) {
    clearInterval(autoLinkTimer);
    autoLinkTimer = null;
  }
}
function linkPrev() {
  if (links.value.length < 2) return;
  currentLinkIdx.value = (currentLinkIdx.value - 1 + links.value.length) % links.value.length;
}
function linkNext() {
  if (links.value.length < 2) return;
  currentLinkIdx.value = (currentLinkIdx.value + 1) % links.value.length;
}
function onLinkStackEnter() {
  stopLinkAuto();
}
function onLinkStackLeave() {
  hoverIdx.value = -1;
  startLinkAuto();
}

function startNoteAuto() {
  stopNoteAuto();
  if (notes.value.length < 2) return;
  autoNoteTimer = setInterval(() => {
    if (draggingNote.value || detailNoteIdx.value >= 0) return;
    currentNoteIdx.value = (currentNoteIdx.value + 1) % notes.value.length;
  }, AUTO_INTERVAL);
}
function stopNoteAuto() {
  if (autoNoteTimer) {
    clearInterval(autoNoteTimer);
    autoNoteTimer = null;
  }
}
function notePrev() {
  if (notes.value.length < 2) return;
  currentNoteIdx.value = (currentNoteIdx.value - 1 + notes.value.length) % notes.value.length;
}
function noteNext() {
  if (notes.value.length < 2) return;
  currentNoteIdx.value = (currentNoteIdx.value + 1) % notes.value.length;
}
function onNoteStackEnter() {
  stopNoteAuto();
}
function onNoteStackLeave() {
  hoverNoteIdx.value = -1;
  startNoteAuto();
}

// ===== 收藏：点击 & 拖拽处理（含抛惯性滑动） =====
function onLinkClick(e: MouseEvent, item: Favorite, i: number) {
  // 拖拽中或刚结束拖拽的点击忽略
  if (dragging.value || Math.abs(dragDeltaX.value) > 5) return;
  // 详情展开时点击不触发访问
  if (detailIdx.value >= 0) return;
  // 非中心卡点击 → 切换为中心
  if (i !== currentLinkIdx.value) {
    currentLinkIdx.value = i;
    return;
  }
  // 中心卡点击 → 访问链接
  openLink(item);
}

// 速度阈值：松手时最近 100ms 内的平均速度 > FLICK_VELOCITY px/ms 视为"抛"
const FLICK_VELOCITY = 0.5;
// 卡片间距（与 linkStyle 的 offset * 260 一致）
const LINK_SPACING = 260;

function onLinkPointerDown(e: PointerEvent, i: number) {
  // 仅中心卡可拖拽
  if (i !== currentLinkIdx.value) return;
  if (detailIdx.value >= 0) return;
  dragIdx.value = i;
  dragStartX.value = e.clientX;
  dragDeltaX.value = 0;
  dragging.value = true;
  linkDragHistory = [{ t: performance.now(), x: e.clientX }];
  (e.target as Element).setPointerCapture?.(e.pointerId);
  window.addEventListener("pointermove", onLinkPointerMove);
  window.addEventListener("pointerup", onLinkPointerUp);
}

function onLinkPointerMove(e: PointerEvent) {
  if (!dragging.value) return;
  dragDeltaX.value = e.clientX - dragStartX.value;
  // 记录速度历史（最近 6 帧）
  const now = performance.now();
  linkDragHistory.push({ t: now, x: e.clientX });
  // 只保留最近 120ms 的样本
  while (linkDragHistory.length > 6 && now - linkDragHistory[0].t > 120) {
    linkDragHistory.shift();
  }
}

function onLinkPointerUp() {
  window.removeEventListener("pointermove", onLinkPointerMove);
  window.removeEventListener("pointerup", onLinkPointerUp);
  if (!dragging.value) return;
  dragging.value = false;

  // 计算松手速度（最近 100ms 内的平均速度）
  const last = linkDragHistory[linkDragHistory.length - 1];
  const first = linkDragHistory[0];
  const dt = last && first ? last.t - first.t : 0;
  const dx = last && first ? last.x - first.x : 0;
  const velocity = dt > 0 ? dx / dt : 0;  // px/ms

  const dist = Math.abs(dragDeltaX.value);

  if (Math.abs(velocity) >= FLICK_VELOCITY) {
    // 快速抛出 → 惯性滑动
    // 惯性距离 = 速度 * 衰减时间（约 600ms 衰减）
    const inertiaDist = velocity * 600;
    // 总位移 = 当前 dragDeltaX + 惯性距离
    const totalDelta = dragDeltaX.value + inertiaDist;
    // 目标卡片数（四舍五入；负数表示往左切，正数往右切）
    // 注意：dragDeltaX 正值 = 往右拖 = 切换到上一张（currentIdx - cards）
    const targetCards = Math.round(-totalDelta / LINK_SPACING);
    startLinkInertia(targetCards);
    return;
  }

  // 慢速拖拽 → 原逻辑：≥ 阈值抽出详情，否则弹回
  if (dist >= DRAG_THRESHOLD) {
    detailIdx.value = dragIdx.value;
  }
  const idx = dragIdx.value;
  dragIdx.value = -1;
  // 弹回：dragDeltaX → 0（让 cubic-bezier 过冲过渡生效）
  dragDeltaX.value = 0;
  linkDragHistory = [];
}

// 惯性滑动动画：让 dragDeltaX 从当前值平滑变到 -targetCards * SPACING（即切换 currentIdx）
function startLinkInertia(targetCards: number) {
  if (linkInertiaRAF) cancelAnimationFrame(linkInertiaRAF);
  const startDelta = dragDeltaX.value;
  const endDelta = -targetCards * LINK_SPACING;
  const duration = 700;  // ms
  const startT = performance.now();
  inertiaLinkRunning.value = true;
  dragIdx.value = -1;

  function step(now: number) {
    const elapsed = now - startT;
    const t = Math.min(1, elapsed / duration);
    // ease-out cubic：先快后慢，模拟惯性衰减
    const eased = 1 - Math.pow(1 - t, 3);
    dragDeltaX.value = startDelta + (endDelta - startDelta) * eased;
    if (t < 1) {
      linkInertiaRAF = requestAnimationFrame(step);
    } else {
      // 动画结束：currentIdx 推进 targetCards（循环 mod n），dragDeltaX 归零
      const n = links.value.length;
      if (n > 0) {
        currentLinkIdx.value = ((currentLinkIdx.value + targetCards) % n + n) % n;
      }
      dragDeltaX.value = 0;
      inertiaLinkRunning.value = false;
      linkInertiaRAF = null;
      linkDragHistory = [];
    }
  }
  linkInertiaRAF = requestAnimationFrame(step);
}

function closeDetail() {
  detailIdx.value = -1;
}

// ===== 笔记：点击 & 拖拽处理（含抛惯性滑动） =====
function onNoteClick(e: MouseEvent, item: Favorite, i: number) {
  if (draggingNote.value || Math.abs(dragNoteDeltaX.value) > 5) return;
  if (detailNoteIdx.value >= 0) return;
  // 非中心卡点击 → 切换为中心
  if (i !== currentNoteIdx.value) {
    currentNoteIdx.value = i;
    return;
  }
  // 中心卡点击 → 展开详情
  detailNoteIdx.value = i;
}

const NOTE_SPACING = 234;

function onNotePointerDown(e: PointerEvent, i: number) {
  if (i !== currentNoteIdx.value) return;
  if (detailNoteIdx.value >= 0) return;
  dragNoteIdx.value = i;
  dragNoteStartX.value = e.clientX;
  dragNoteDeltaX.value = 0;
  draggingNote.value = true;
  noteDragHistory = [{ t: performance.now(), x: e.clientX }];
  (e.target as Element).setPointerCapture?.(e.pointerId);
  window.addEventListener("pointermove", onNotePointerMove);
  window.addEventListener("pointerup", onNotePointerUp);
}

function onNotePointerMove(e: PointerEvent) {
  if (!draggingNote.value) return;
  dragNoteDeltaX.value = e.clientX - dragNoteStartX.value;
  const now = performance.now();
  noteDragHistory.push({ t: now, x: e.clientX });
  while (noteDragHistory.length > 6 && now - noteDragHistory[0].t > 120) {
    noteDragHistory.shift();
  }
}

function onNotePointerUp() {
  window.removeEventListener("pointermove", onNotePointerMove);
  window.removeEventListener("pointerup", onNotePointerUp);
  if (!draggingNote.value) return;
  draggingNote.value = false;

  // 计算松手速度
  const last = noteDragHistory[noteDragHistory.length - 1];
  const first = noteDragHistory[0];
  const dt = last && first ? last.t - first.t : 0;
  const dx = last && first ? last.x - first.x : 0;
  const velocity = dt > 0 ? dx / dt : 0;

  const dist = Math.abs(dragNoteDeltaX.value);

  if (Math.abs(velocity) >= FLICK_VELOCITY) {
    // 快速抛出 → 惯性滑动
    const inertiaDist = velocity * 600;
    const totalDelta = dragNoteDeltaX.value + inertiaDist;
    const targetCards = Math.round(-totalDelta / NOTE_SPACING);
    startNoteInertia(targetCards);
    return;
  }

  // 慢速拖拽 → 原逻辑
  if (dist >= DRAG_THRESHOLD) {
    detailNoteIdx.value = dragNoteIdx.value;
  }
  dragNoteIdx.value = -1;
  dragNoteDeltaX.value = 0;
  noteDragHistory = [];
}

function startNoteInertia(targetCards: number) {
  if (noteInertiaRAF) cancelAnimationFrame(noteInertiaRAF);
  const startDelta = dragNoteDeltaX.value;
  const endDelta = -targetCards * NOTE_SPACING;
  const duration = 700;
  const startT = performance.now();
  inertiaNoteRunning.value = true;
  dragNoteIdx.value = -1;

  function step(now: number) {
    const elapsed = now - startT;
    const t = Math.min(1, elapsed / duration);
    const eased = 1 - Math.pow(1 - t, 3);
    dragNoteDeltaX.value = startDelta + (endDelta - startDelta) * eased;
    if (t < 1) {
      noteInertiaRAF = requestAnimationFrame(step);
    } else {
      const n = notes.value.length;
      if (n > 0) {
        currentNoteIdx.value = ((currentNoteIdx.value + targetCards) % n + n) % n;
      }
      dragNoteDeltaX.value = 0;
      inertiaNoteRunning.value = false;
      noteInertiaRAF = null;
      noteDragHistory = [];
    }
  }
  noteInertiaRAF = requestAnimationFrame(step);
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
    await ElMessageBox.confirm(
      `确定要删除「${item.name}」吗？删除后不可恢复！`,
      "删除确认",
      {
        type: "warning",
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        confirmButtonClass: "ld-del-confirm-btn"
      }
    );
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

onMounted(async () => {
  await load();
  startLinkAuto();
  startNoteAuto();
});

// 组件卸载前清理全局监听与定时器
onBeforeUnmount(() => {
  window.removeEventListener("pointermove", onLinkPointerMove);
  window.removeEventListener("pointerup", onLinkPointerUp);
  window.removeEventListener("pointermove", onNotePointerMove);
  window.removeEventListener("pointerup", onNotePointerUp);
  if (linkInertiaRAF) cancelAnimationFrame(linkInertiaRAF);
  if (noteInertiaRAF) cancelAnimationFrame(noteInertiaRAF);
  stopLinkAuto();
  stopNoteAuto();
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
  max-width: 720px;
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
  padding: 14px 18px 12px;
  border-bottom: 1px solid $border-glass;
  gap: 10px;
}
.pane-head-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.pane-head-right {
  display: flex;
  align-items: center;
  gap: 12px;
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
.pane-create {
  font-size: 12px;
  padding: 5px 11px;
  white-space: nowrap;
}
.pane-body {
  flex: 1;
  overflow: hidden;
  padding: 14px 18px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;  /* 轮播图位于所属区域中间部位 */
}
.empty {
  text-align: center;
  color: $text-tertiary;
  padding: 60px 0;
  font-size: 13px;
}

/* ========== 3D 立体堆叠容器（卡片再加大 30%） ========== */
.card-stack {
  position: relative;
  width: 100%;
  max-width: 494px;    /* 380 → 494（+30%），宽度不变 */
  height: 364px;       /* 455 → 364，高度调低 20% */
  perspective: 1600px;  /* 卡片加大，透视距离同步加大避免畸变 */
  perspective-origin: 50% 55%;
  transform-style: preserve-3d;
  /* --btn-top：按钮垂直位置，收藏默认在 info 区（卡片下半截，约 73% 处） */
  --btn-top: 236px;    /* 295 → 236，配合卡片高度 322 的下半截 */
}
.note-stack {
  height: 364px;       /* 280 → 364 */
  /* 笔记按钮居中到卡片中部（卡片高 182 / 2 = 91px） */
  --btn-top: 91px;     /* 70 → 91 */
}

/* ========== 轮播卡片（共享样式，加大 30%） ========== */
.stack-card {
  position: absolute;
  top: 0;
  left: 50%;
  margin-left: -221px;  /* 卡宽 442 / 2 */
  width: 442px;         /* 340 → 442 */
  border-radius: 18px;  /* 16 → 18，配合卡片加大 */
  backface-visibility: hidden;
  cursor: pointer;
  user-select: none;
  transform-origin: center center;
  /* 玻璃风：半透明 + 内高光 */
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.03));
  border: 1px solid $border-glass;
  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.38), inset 0 1px 0 rgba(255, 255, 255, 0.12);
  overflow: hidden;
  will-change: transform, opacity;
}
/* 中心卡：边框高亮 + 阴影更亮，强调"当前展示" */
.stack-card.center {
  border-color: $border-accent;
  box-shadow: 0 22px 58px rgba(0, 0, 0, 0.55),
              0 0 0 1px rgba(196, 181, 253, 0.28),
              0 0 34px rgba(167, 139, 250, 0.3),
              inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
.stack-card.dim {
  filter: brightness(0.55) saturate(0.7);
}
.stack-card.dragging {
  cursor: grabbing;
}

/* ========== 收藏卡片（加大 30%） ========== */
.link-card {
  height: 322px;       /* 403 → 322，高度调低 20% */
  display: flex;
  flex-direction: column;
}
/* ========== 主题色横幅（替代网页截图，取自 8 张微信图片主题色） ========== */
.theme-banner {
  position: relative;
  width: 100%;
  height: 150px;
  overflow: hidden;
  border-bottom: 1px solid $border-subtle;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 右上光斑装饰 */
  &::before {
    content: '';
    position: absolute;
    top: -40px;
    right: -40px;
    width: 140px;
    height: 140px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.12);
    filter: blur(2px);
  }
  &::after {
    content: '';
    position: absolute;
    bottom: -50px;
    left: -30px;
    width: 120px;
    height: 120px;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.14);
    filter: blur(2px);
  }
}
.banner-letter {
  font-size: 64px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.92);
  text-shadow: 0 3px 14px rgba(0, 0, 0, 0.35);
  letter-spacing: 2px;
  z-index: 1;
}
.detail-letter {
  font-size: 88px;
}
.info {
  flex: 1;
  padding: 14px 18px 12px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  /* 下半截填充更深的背景色，让 name/url/tags 文字更突出 */
  background: linear-gradient(180deg, rgba(12, 8, 26, 0.72), rgba(12, 8, 26, 0.92));
  border-top: 1px solid rgba(196, 181, 253, 0.14);
}
.name {
  font-size: 19px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.3px;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.5);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.url {
  font-size: 13px;
  color: #d8ccff;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 5px;
}
.tag {
  font-size: 11px;
  padding: 3px 9px;
  border-radius: 9px;
  font-weight: 700;
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
/* 环境标识：测试青绿 / 集成橙黄 / 生产红 —— 字体颜色更饱和 + 小圆点 + 加粗，一眼可辨 */
.tag.env {
  font-size: 11px;
  padding: 3px 9px 3px 7px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  letter-spacing: 0.4px;
  &::before {
    content: '';
    display: inline-block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
    box-shadow: 0 0 4px currentColor;
  }
}
.env.env-test {
  background: rgba(45, 212, 191, 0.22);
  color: #2dd4bf;       /* 更饱和的青绿 */
  border: 1px solid rgba(45, 212, 191, 0.55);
}
.env.env-integration {
  background: rgba(251, 191, 36, 0.22);
  color: #fbbf24;      /* 更饱和的橙黄 */
  border: 1px solid rgba(251, 191, 36, 0.55);
}
.env.env-prod {
  background: rgba(239, 68, 68, 0.26);
  color: #f87171;      /* 更饱和的红 */
  border: 1px solid rgba(239, 68, 68, 0.6);
  font-weight: 800;
}
.tag.desc {
  background: rgba(167, 139, 250, 0.12);
  color: $text-secondary;
  border: 1px solid rgba(167, 139, 250, 0.18);
}

/* ========== 左右轮播箭头按钮（更靠边 + hover 更明显 + 防误点热区） ========== */
/* ========== 左右轮播箭头（.arrow-zone 父容器方案，彻底修复连续点击穿透） ==========
   关键：用独立 div.arrow-zone 作为热区层（80x80），::after 画中心 44x44 圆形视觉，
   el-icon z-index:1 在 ::after 之上。z-index 9999 远高于卡片任何状态
   （中心卡 hover 1000 / dragging 2000），杜绝"鼠标不动再次点击穿透到下方卡片"。
   点击 .arrow-zone 任何位置（包括圆形外透明区）都触发 click → prev/next，
   支持连续点击连续切换。 */
.arrow-zone {
  position: absolute;
  top: var(--btn-top, 50%);
  transform: translateY(-50%);
  width: 80px;        /* 热区 80x80 */
  height: 80px;
  z-index: 9999;      /* 远高于卡片，杜绝穿透 */
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background: transparent;
  border: none;
  outline: none;
  /* 中心圆形视觉 */
  &::after {
    content: '';
    position: absolute;
    width: 44px;
    height: 44px;
    border-radius: 50%;
    /* 平时填充背景颜色 + 紫色边框 + 紫色发光，让按钮文字（箭头）展示更突出 */
    background: rgba(20, 14, 38, 0.85);
    border: 1px solid rgba(196, 181, 253, 0.55);
    box-shadow: 0 0 14px rgba(167, 139, 250, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.12);
    transition: all 0.22s cubic-bezier(0.34, 1.56, 0.64, 1);
    z-index: 0;
  }
  .el-icon {
    position: relative;
    z-index: 1;        /* 图标在圆形视觉之上 */
    color: rgba(196, 181, 253, 1);
    font-size: 18px;
    transition: all 0.22s;
  }
  &:hover {
    &::after {
      background: linear-gradient(135deg, rgba(167, 139, 250, 0.6), rgba(196, 181, 253, 0.42));
      border-color: rgba(196, 181, 253, 0.95);
      box-shadow: 0 0 26px rgba(167, 139, 250, 0.7),
                  0 0 0 2px rgba(196, 181, 253, 0.6),
                  inset 0 1px 0 rgba(255, 255, 255, 0.28);
      transform: scale(1.18);
    }
    .el-icon {
      color: #fff;
    }
  }
  &:active::after {
    transform: scale(0.95);
  }
}
.arrow-zone-left {
  left: -18px;        /* 圆形中心位于 card-stack 左边缘 4px 处，按钮更靠边 */
}
.arrow-zone-right {
  right: -18px;
}

.card-actions {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 8px;
  /* 常显（不再 hover 才出现），一眼可见 */
  opacity: 0.92;
  z-index: 5;
}
.op {
  cursor: pointer;
  color: #fff;
  font-size: 18px;
  padding: 8px;
  border-radius: 9px;
  display: flex;
  transition: all 0.15s;
  /* 编辑：紫色实底，醒目 */
  &.op-edit {
    background: linear-gradient(135deg, #8b5cf6, #7c3aed);
    box-shadow: 0 3px 10px rgba(139, 92, 246, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.25);
    &:hover {
      transform: scale(1.12);
      box-shadow: 0 4px 14px rgba(139, 92, 246, 0.65), inset 0 1px 0 rgba(255, 255, 255, 0.25);
    }
  }
  /* 删除：红色实底，警示 */
  &.op-del {
    background: linear-gradient(135deg, #ef4444, #dc2626);
    box-shadow: 0 3px 10px rgba(239, 68, 68, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.25);
    &:hover {
      transform: scale(1.12);
      box-shadow: 0 4px 14px rgba(239, 68, 68, 0.65), inset 0 1px 0 rgba(255, 255, 255, 0.25);
    }
  }
  &:active {
    transform: scale(0.94);
  }
}

/* ========== 笔记卡片（加大 30%） ========== */
.note-card {
  height: 182px;       /* 140 → 182 */
  padding: 16px 20px;  /* 14x16 → 16x20，配合卡片加大 */
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.note-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.note-icon {
  color: $accent-light;
  font-size: 18px;     /* 15 → 18 */
}
.note-title {
  font-size: 15px;     /* 13 → 15 */
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
  gap: 8px;
}
.note-tag {
  font-size: 11px;
  color: $text-tertiary;
  background: rgba(167, 139, 250, 0.08);
  padding: 3px 10px;
  border-radius: 8px;
}
.note-time {
  font-size: 12px;
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
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
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
  color: #d8ccff;
  font-size: 15px;
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
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: $text-secondary;
  line-height: 1.7;
  background: rgba(0, 0, 0, 0.25);
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid $border-subtle;
  max-height: 60vh;
  overflow: auto;
}
/* 每行：文本 + hover 显示的复制按钮 */
.note-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 1px 4px;
  border-radius: 6px;
  transition: background 0.15s;
  &:hover {
    background: rgba(167, 139, 250, 0.1);
  }
}
.line-text {
  flex: 1;
  white-space: pre-wrap;
  word-break: break-word;
}
.line-copy {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-top: 2px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: $text-tertiary;
  font-size: 13px;
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s;
  .note-line:hover & {
    opacity: 1;
  }
  &:hover {
    color: $accent-light;
    background: rgba(167, 139, 250, 0.18);
    box-shadow: 0 0 8px rgba(167, 139, 250, 0.3);
  }
  &:active {
    transform: scale(0.9);
  }
}
.empty-line {
  color: $text-tertiary;
  justify-content: center;
  padding: 10px 0;
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
