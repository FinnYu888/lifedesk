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
      <!-- ========== 收藏：3D 左右立体轮播（cover flow） ========== -->
      <div class="ld-card pane">
        <div class="pane-head">
          <div class="pane-head-left">
            <span class="pane-title">收藏</span>
            <span class="pane-count">{{ links.length }} 项</span>
          </div>
          <div class="pane-tip">自动轮播 · 悬停弹起停止 · 点箭头切换</div>
        </div>
        <div class="pane-body">
          <div v-if="links.length === 0" class="empty">暂无收藏，点击右上「新建收藏」</div>
          <div
            v-else
            class="card-stack"
            @mouseenter="onLinkStackEnter"
            @mouseleave="onLinkStackLeave"
          >
            <!-- 左箭头 -->
            <button
              v-if="links.length > 1"
              class="arrow-btn arrow-left"
              @click="linkPrev"
              aria-label="上一张"
            >
              <el-icon><ArrowLeft /></el-icon>
            </button>

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

            <!-- 右箭头 -->
            <button
              v-if="links.length > 1"
              class="arrow-btn arrow-right"
              @click="linkNext"
              aria-label="下一张"
            >
              <el-icon><ArrowRight /></el-icon>
            </button>

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

      <!-- ========== 笔记：3D 左右立体轮播（简单标题卡） ========== -->
      <div class="ld-card pane">
        <div class="pane-head">
          <div class="pane-head-left">
            <span class="pane-title">笔记</span>
            <span class="pane-count">{{ notes.length }} 项</span>
          </div>
          <div class="pane-tip">自动轮播 · 悬停弹起停止 · 点箭头切换</div>
        </div>
        <div class="pane-body">
          <div v-if="notes.length === 0" class="empty">暂无笔记</div>
          <div
            v-else
            class="card-stack note-stack"
            @mouseenter="onNoteStackEnter"
            @mouseleave="onNoteStackLeave"
          >
            <!-- 左箭头 -->
            <button
              v-if="notes.length > 1"
              class="arrow-btn arrow-left"
              @click="notePrev"
              aria-label="上一篇"
            >
              <el-icon><ArrowLeft /></el-icon>
            </button>

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
                <el-icon class="op" @click="openEdit(item)"><Edit /></el-icon>
                <el-icon class="op danger" @click="onDelete(item)"><Delete /></el-icon>
              </div>
            </div>

            <!-- 右箭头 -->
            <button
              v-if="notes.length > 1"
              class="arrow-btn arrow-right"
              @click="noteNext"
              aria-label="下一篇"
            >
              <el-icon><ArrowRight /></el-icon>
            </button>

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
  ArrowUp, ArrowDown, ArrowLeft, ArrowRight, Close, Picture
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
const dragDeltaX = ref(0);
const dragging = ref(false);
const detailIdx = ref(-1);
const imgLoaded = ref<Record<number, boolean>>({});
// cover flow 中心卡索引
const currentLinkIdx = ref(0);
let autoLinkTimer: any = null;
const AUTO_INTERVAL = 3500;

// ===== 笔记：3D 轮播交互状态 =====
const hoverNoteIdx = ref(-1);
const dragNoteIdx = ref(-1);
const dragNoteStartX = ref(0);
const dragNoteDeltaX = ref(0);
const draggingNote = ref(false);
const detailNoteIdx = ref(-1);
const currentNoteIdx = ref(0);
let autoNoteTimer: any = null;

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

// ===== 立体轮播（cover flow）transform 计算 =====
// offset = i - currentIdx（相对中心的偏移，左侧为负、右侧为正）
// 卡片按 offset 做 translateX + rotateY + translateZ + scale，形成左右立体轮播
function linkStyle(i: number) {
  const n = links.value.length;
  const offset = i - currentLinkIdx.value;
  const abs = Math.abs(offset);
  // cover flow 排布：左右展开 + 侧面旋转 + 远处下沉（更紧凑的间距）
  let x = offset * 175;
  let y = 0;
  let z = -abs * 85;
  let scale = Math.max(0.64, 1 - abs * 0.13);
  let rot = offset * -36;  // 左侧卡 rotateY 正（向右转）、右侧卡负
  let zIdx = 100 - abs;
  let opacity = Math.max(0.38, 1 - abs * 0.18);

  // 鼠标 hover 弹起：该卡大幅前移 + 放大；其他卡维持
  if (hoverIdx.value === i) {
    z = z + 80;
    scale = Math.max(scale, 1.1);
    y = -8;
    rot = rot * 0.25;  // hover 时旋转减弱（更正面展示）
    zIdx = 1000;
  }

  // 拖拽中心卡抽出
  if (dragging.value && dragIdx.value === i) {
    x = offset * 175 + dragDeltaX.value;
    z = 130;
    scale = 1.12;
    y = -14;
    rot = dragDeltaX.value * 0.18;
    zIdx = 2000;
  } else if (dragging.value && dragIdx.value === currentLinkIdx.value) {
    // 后方卡（非中心）按重量 abs 下沉
    const weight = abs * 0.45 + 0.2;
    const dragAbs = Math.abs(dragDeltaX.value);
    y = dragAbs * weight * 0.32;
    z = -abs * 85 - dragAbs * weight * 0.12;
    scale = Math.max(0.5, scale - dragAbs * weight * 0.0006);
  }

  const transitionMode =
    (dragging.value && dragIdx.value === i)
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
  const offset = i - currentNoteIdx.value;
  const abs = Math.abs(offset);
  let x = offset * 160;
  let y = 0;
  let z = -abs * 78;
  let scale = Math.max(0.66, 1 - abs * 0.12);
  let rot = offset * -34;
  let zIdx = 100 - abs;
  let opacity = Math.max(0.4, 1 - abs * 0.16);

  if (hoverNoteIdx.value === i) {
    z = z + 70;
    scale = Math.max(scale, 1.08);
    y = -6;
    rot = rot * 0.25;
    zIdx = 1000;
  }

  if (draggingNote.value && dragNoteIdx.value === i) {
    x = offset * 160 + dragNoteDeltaX.value;
    z = 125;
    scale = 1.1;
    y = -12;
    rot = dragNoteDeltaX.value * 0.18;
    zIdx = 2000;
  } else if (draggingNote.value && dragNoteIdx.value === currentNoteIdx.value) {
    const weight = abs * 0.45 + 0.2;
    const dragAbs = Math.abs(dragNoteDeltaX.value);
    y = dragAbs * weight * 0.32;
    z = -abs * 78 - dragAbs * weight * 0.12;
    scale = Math.max(0.55, scale - dragAbs * weight * 0.0006);
  }

  const transitionMode =
    (draggingNote.value && dragNoteIdx.value === i)
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

// ===== 收藏：点击 & 拖拽处理 =====
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

function onLinkPointerDown(e: PointerEvent, i: number) {
  // 仅中心卡可拖拽抽出
  if (i !== currentLinkIdx.value) return;
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
  // 非中心卡点击 → 切换为中心
  if (i !== currentNoteIdx.value) {
    currentNoteIdx.value = i;
    return;
  }
  // 中心卡点击 → 展开详情
  detailNoteIdx.value = i;
}

function onNotePointerDown(e: PointerEvent, i: number) {
  if (i !== currentNoteIdx.value) return;
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
  padding: 14px 18px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;  /* 改为垂直居中：轮播图位于所属区域中间部位 */
}
.empty {
  text-align: center;
  color: $text-tertiary;
  padding: 60px 0;
  font-size: 13px;
}

/* ========== 3D 立体堆叠容器（更紧凑） ========== */
.card-stack {
  position: relative;
  width: 100%;
  max-width: 340px;  /* 420 → 340，更紧凑 */
  height: 320px;     /* 380 → 320 */
  perspective: 1400px;
  perspective-origin: 50% 60%;
  transform-style: preserve-3d;
}
.note-stack {
  height: 260px;     /* 320 → 260 */
}

/* ========== 轮播卡片（共享样式） ========== */
.stack-card {
  position: absolute;
  top: 0;
  left: 50%;
  margin-left: -150px;  /* 卡宽 300 / 2 */
  width: 300px;         /* 360 → 300，更紧凑 */
  border-radius: 16px;
  backface-visibility: hidden;
  cursor: pointer;
  user-select: none;
  transform-origin: center center;
  /* 玻璃风：半透明 + 内高光 */
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.03));
  border: 1px solid $border-glass;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.12);
  overflow: hidden;
  will-change: transform, opacity;
}
/* 中心卡：边框高亮 + 阴影更亮，强调"当前展示" */
.stack-card.center {
  border-color: $border-accent;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.5),
              0 0 0 1px rgba(196, 181, 253, 0.25),
              0 0 28px rgba(167, 139, 250, 0.25),
              inset 0 1px 0 rgba(255, 255, 255, 0.18);
}
.stack-card.dim {
  filter: brightness(0.55) saturate(0.7);
}
.stack-card.dragging {
  cursor: grabbing;
}

/* ========== 收藏卡片 ========== */
.link-card {
  height: 280px;       /* 320 → 280 */
  display: flex;
  flex-direction: column;
}
.screenshot {
  position: relative;
  width: 100%;
  height: 150px;       /* 180 → 150 */
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
  padding: 10px 14px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.name {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.url {
  font-size: 10px;
  color: $accent-light;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 4px;
}
.tag {
  font-size: 10px;
  padding: 2px 7px;
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
.arrow-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(20, 14, 38, 0.6);
  border: 1px solid rgba(196, 181, 253, 0.22);
  color: rgba(196, 181, 253, 0.75);
  font-size: 18px;
  cursor: pointer;
  z-index: 1500;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(8px);
  transition: all 0.22s cubic-bezier(0.34, 1.56, 0.64, 1);
  /* 防误点：用伪元素把按钮的有效点击热区扩大到 88x88（按钮周围 22px 内均归按钮捕获，
     避免误点击到按钮下方/旁边的卡片导致选错卡片） */
  &::before {
    content: '';
    position: absolute;
    inset: -22px;       /* 热区扩展 22px → 总热区 88x88 */
    border-radius: 50%;
    cursor: pointer;
    /* 透明，不影响视觉 */
  }
  &:hover {
    color: #fff;
    border-color: rgba(196, 181, 253, 0.95);
    background: linear-gradient(135deg, rgba(167, 139, 250, 0.45), rgba(196, 181, 253, 0.32));
    box-shadow: 0 0 24px rgba(167, 139, 250, 0.6),
                0 0 0 2px rgba(196, 181, 253, 0.55),
                inset 0 1px 0 rgba(255, 255, 255, 0.25);
    transform: translateY(-50%) scale(1.18);
  }
  &:active {
    transform: translateY(-50%) scale(0.95);
  }
}
.arrow-left {
  left: 0;            /* 8px → 0，更靠边 */
}
.arrow-right {
  right: 0;           /* 8px → 0，更靠边 */
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
  height: 120px;       /* 140 → 120，更紧凑 */
  padding: 12px 16px;
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
  font-size: 15px;
}
.note-title {
  font-size: 13px;
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
