<template>
  <div class="project">
    <!-- 统计卡行 -->
    <div class="stat-row">
      <StatCard label="活跃项目" :value="stat.active_projects ?? 0" note="未删除项目" noteType="positive" />
      <StatCard label="本周提交" :value="stat.week_commits ?? 0" :note="stat.week_commits_trend || '+0'" noteType="positive" />
      <StatCard label="合并请求" :value="stat.pending_prs ?? 0" note="累计已合并 PR" noteType="positive" />
      <StatCard label="本周新仓库" :value="stat.new_repos ?? 0" note="近 7 天新增" noteType="neutral" />
    </div>

    <div class="dual-row">
      <!-- 活跃项目列表 -->
      <div class="ld-card">
        <div class="card-head">
          <div class="card-title">活跃项目</div>
          <el-button type="primary" size="small" :icon="Plus" @click="openCreateDialog">新建项目</el-button>
        </div>

        <div v-if="!active.length" class="empty">暂无项目，点右上角新建</div>

        <div v-for="p in active" :key="p.id" class="proj-row">
          <span class="color-dot" :class="dotCls(p)" />
          <div class="proj-info">
            <div class="proj-name">
              {{ p.name }}
              <span v-if="p.tech" class="tech-tag">{{ p.tech }}</span>
              <span v-if="p.gitBranch" class="branch-tag">⎇ {{ p.gitBranch }}</span>
            </div>
            <div class="proj-desc">{{ p.description }}</div>
            <div v-if="p.gitUrl" class="proj-git">
              <span class="git-stat" :title="p.gitUrl">git: {{ shortenUrl(p.gitUrl) }}</span>
              <span class="git-stat">{{ p.commitsCount ?? 0 }} commits</span>
              <span class="git-stat">{{ p.prsCount ?? 0 }} PRs</span>
              <span class="git-stat">{{ p.releasesCount ?? 0 }} releases</span>
              <span class="git-stat push-time">最近推送 {{ pushTimeAgo(p.lastSyncAt) }}</span>
              <span
                class="git-stat sync-tag"
                :class="syncTagCls(p)"
                :title="p.syncError || syncTagTip(p)"
              >{{ syncTagText(p) }}</span>
            </div>
          </div>
          <div class="proj-right">
            <div class="proj-time text-tertiary">{{ p.lastActive || '—' }}</div>
            <div class="proj-actions">
              <el-button
                size="small"
                :icon="Edit"
                :loading="editingId === p.id"
                title="编辑项目"
                @click="openEditDialog(p)"
              />
              <el-button
                size="small"
                :icon="Refresh"
                :loading="syncingId === p.id"
                :disabled="!p.gitUrl"
                :title="p.gitUrl ? '同步 Git 数据' : '未配置 Git 地址'"
                @click="onSync(p)"
              />
              <el-button
                size="small"
                type="danger"
                :icon="Delete"
                :loading="deletingId === p.id"
                title="删除项目"
                @click="onDelete(p)"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- 我的需求（第三方需求平台拉取） -->
      <div class="ld-card req-card">
        <div class="card-head">
          <div class="card-title">我的需求</div>
          <div class="req-filter">
            <el-select
              v-model="reqSprint"
              placeholder="全部 Sprint"
              size="small"
              clearable
              filterable
              style="width: 168px"
              @change="onReqFilterChange"
            >
              <el-option
                v-for="m in milestones"
                :key="m.name"
                :label="m.name"
                :value="m.name"
              />
            </el-select>
            <el-select
              v-model="reqStatus"
              placeholder="全部状态"
              size="small"
              clearable
              style="width: 110px"
              @change="onReqFilterChange"
            >
              <el-option label="待办" value="todo" />
              <el-option label="进行中" value="doing" />
              <el-option label="提测" value="testing" />
              <el-option label="待发版" value="pending" />
              <el-option label="完成" value="done" />
              <el-option label="已取消" value="cancel" />
            </el-select>
            <el-input
              v-model="reqKeyword"
              placeholder="搜索 任务详情/话题/产品/测试人"
              size="small"
              clearable
              style="width: 220px"
              @input="onReqKeywordInput"
              @clear="onReqFilterChange"
            />
          </div>
        </div>
        <!-- authorization 输入区：wetrans.cimc.cn/gateway/ 接口鉴权每日可能过期 -->
        <div class="auth-bar">
          <div class="auth-status" :class="authStatusCls">
            <span class="auth-dot" />
            <span class="auth-text">{{ authStatusText }}</span>
          </div>
          <el-input
            v-model="authInput"
            type="password"
            show-password
            placeholder="粘贴最新 authorization（JWT，不带 Bearer 前缀）"
            size="small"
            class="auth-input"
            @keyup.enter="onSaveAuthorization"
          />
          <el-button
            size="small"
            type="primary"
            :loading="authSaving"
            @click="onSaveAuthorization"
          >保存并刷新</el-button>
        </div>
        <div class="req-tip text-tertiary">
          来源：wetrans.cimc.cn/gateway/apiIssue/developTask/query（点击行查看详情）
        </div>
        <!-- 鉴权过期 / 空数据提示 -->
        <div v-if="!requirements.length && authExpired" class="req-empty-hint">
          鉴权信息已过期，请输入最新 authorization 后点击「保存并刷新」
        </div>
        <el-table
          v-else
          :data="requirements"
          size="small"
          class="req-table"
          :max-height="420"
          empty-text="暂无需求"
          highlight-current-row
          @row-click="onReqRowClick"
          :row-style="{ cursor: 'pointer' }"
        >
          <el-table-column prop="sprint" label="Sprint" width="130" fixed>
            <template #default="{ row }">
              <span class="sprint-tag">{{ row.sprint }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="personDay" label="人/天" width="76" align="center">
            <template #default="{ row }">
              <span class="person-day">{{ row.personDay ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="taskStatus" label="任务状态" width="96">
            <template #default="{ row }">
              <span class="status-tag" :class="reqStatusCls(row.taskStatus)">
                {{ reqStatusText(row.taskStatus) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="topic" label="关联话题" width="120" show-overflow-tooltip />
          <el-table-column prop="taskDetail" label="任务详情" min-width="240">
            <template #default="{ row }">
              <el-tooltip
                :content="row.taskDetail || '—'"
                placement="top"
                :show-after="180"
                :hide-after="0"
                :enterable="false"
                popper-class="req-task-tooltip"
              >
                <div class="task-detail-cell">{{ row.taskDetail || '—' }}</div>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="product" label="产品" width="110" show-overflow-tooltip />
          <el-table-column prop="tester" label="测试人" width="90" show-overflow-tooltip />
        </el-table>
      </div>

      <!-- 里程碑进度（按需求接口 sprint 聚合 + issue/query status=CLOSED 统计） -->
      <div class="ld-card">
        <div class="card-head">
          <div class="card-title">里程碑进度</div>
          <span class="text-tertiary" style="font-size: 12px">按 Sprint 聚合</span>
        </div>
        <div v-if="!milestones.length && authExpired" class="m-empty-hint">
          鉴权信息已过期，请输入最新 authorization 后点击「保存并刷新」
        </div>
        <template v-else>
          <ProgressBar
            v-for="m in milestones"
            :key="m.name"
            :label="milestoneLabel(m)"
            :percent="m.progress ?? 0"
            :color="milestoneColor(m.progress ?? 0)"
          />
          <div class="m-note text-tertiary">
            Sprint 形如 CDS20261025，其中 20261025 即为里程碑日期
          </div>
        </template>
      </div>

      <!-- 最近提交活动（每个项目最后一次提交） -->
      <div class="ld-card">
        <div class="card-title">最近提交活动</div>
        <div class="m-note text-tertiary" style="margin-top:0;margin-bottom:6px">每个项目的最后一次提交</div>
        <div v-if="!commits.length" class="empty">暂无提交动态</div>
        <div v-for="c in commits" :key="c.id" class="commit-row">
          <span class="commit-dot" :style="{ background: commitColor(c.type) }" />
          <div class="commit-info">
            <div class="commit-author">
              {{ c.author }}
              <span v-if="c.projectName" class="commit-proj-tag">{{ c.projectName }}</span>
            </div>
            <div class="commit-msg">{{ c.message }}</div>
          </div>
          <span class="commit-time text-tertiary">{{ c.timeAgo || pushTimeAgo(c.committedAt) }}</span>
        </div>
      </div>
    </div>

    <!-- 新建/编辑项目弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑项目' : '新建项目'"
      width="520px"
      :close-on-click-modal="false"
      class="ld-dialog"
      modal-class="ld-overlay"
    >
      <el-form label-width="96px" label-position="left">
        <el-form-item label="项目名" required>
          <el-input v-model="form.name" placeholder="如 payment-service" maxlength="64" />
        </el-form-item>
        <el-form-item label="技术栈">
          <el-input v-model="form.tech" placeholder="如 Go / React / Java" maxlength="32" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="一句话描述项目用途" maxlength="200" />
        </el-form-item>
        <el-divider content-position="left">Git 信息（选填，填写后会拉取提交/合并/发布）</el-divider>
        <el-form-item label="仓库类型">
          <el-radio-group v-model="form.gitPlatform">
            <el-radio-button label="generic">通用</el-radio-button>
            <el-radio-button label="github">GitHub</el-radio-button>
            <el-radio-button label="gitlab">GitLab</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Git 地址">
          <el-input v-model="form.gitUrl" :placeholder="gitUrlPlaceholder" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.gitUsername" :placeholder="usernamePlaceholder" autocomplete="off" />
        </el-form-item>
        <el-form-item label="密码/Token">
          <el-input
            v-model="form.gitPassword"
            type="password"
            show-password
            :placeholder="form.id ? '留空表示不修改原密码/Token' : passwordPlaceholder"
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="使用分支">
          <el-input v-model="form.gitBranch" placeholder="main / master / develop" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ form.id ? '保存' : '创建并同步' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 需求详情弹窗：点击列表行触发，调用 developTask/getDetail?id=... -->
    <el-dialog
      v-model="detailDialog.visible"
      title="需求详情"
      width="760px"
      :close-on-click-modal="true"
      class="ld-dialog"
      modal-class="ld-overlay"
    >
      <div v-loading="detailDialog.loading" class="req-detail-body">
        <template v-if="detailDialog.data">
          <!-- 标题区 -->
          <div class="detail-title">
            <span v-if="detailDialog.data.taskNo" class="detail-task-no">
              {{ detailDialog.data.taskNo }}
            </span>
            <span class="detail-title-text">
              {{ detailDialog.data.title || '（无标题）' }}
            </span>
            <span
              v-if="detailDialog.data.status"
              class="status-tag"
              :class="reqStatusCls(mapRemoteStatusToLocal(detailDialog.data.status))"
              style="margin-left: 8px"
            >
              {{ reqStatusText(mapRemoteStatusToLocal(detailDialog.data.status)) }}
            </span>
          </div>

          <!-- 元信息网格 -->
          <div class="detail-meta">
            <div class="meta-item">
              <span class="meta-label">Sprint</span>
              <span class="meta-value">{{ detailDialog.data.sprintName || detailDialog.data.sprintId || '—' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">人/天</span>
              <span class="meta-value">{{ detailDialog.data.personDays ?? '—' }}</span>
            </div>
            <div class="meta-item" v-if="detailDialog.data.issueNo">
              <span class="meta-label">关联话题</span>
              <span class="meta-value">{{ detailDialog.data.issueNo }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">产品</span>
              <span class="meta-value">
                {{ detailDialog.data.productOwnerName || detailDialog.data.productOwner || '—' }}
              </span>
            </div>
            <div class="meta-item">
              <span class="meta-label">测试人</span>
              <span class="meta-value">
                {{ detailDialog.data.testUserName || detailDialog.data.testUser || '—' }}
              </span>
            </div>
            <div class="meta-item" v-if="detailDialog.data.deadlineTime">
              <span class="meta-label">截止时间</span>
              <span class="meta-value">{{ formatTime(detailDialog.data.deadlineTime) }}</span>
            </div>
            <div class="meta-item" v-if="detailDialog.data.createUserName">
              <span class="meta-label">创建人</span>
              <span class="meta-value">{{ detailDialog.data.createUserName }}</span>
            </div>
            <div class="meta-item" v-if="detailDialog.data.createTime">
              <span class="meta-label">创建时间</span>
              <span class="meta-value">{{ formatTime(detailDialog.data.createTime) }}</span>
            </div>
            <div class="meta-item" v-if="detailDialog.data.updateUserName">
              <span class="meta-label">更新人</span>
              <span class="meta-value">{{ detailDialog.data.updateUserName }}</span>
            </div>
            <div class="meta-item" v-if="detailDialog.data.updateTime">
              <span class="meta-label">更新时间</span>
              <span class="meta-value">{{ formatTime(detailDialog.data.updateTime) }}</span>
            </div>
          </div>

          <!-- 详情正文：title + content（v-html 渲染，含图片代理 + 段落/列表/图片原生展示） -->
          <div class="detail-section">
            <div class="detail-section-title">标题</div>
            <div class="detail-title-display">
              {{ detailDialog.data.title || '（无标题）' }}
            </div>
          </div>
          <div class="detail-section">
            <div class="detail-section-title">详情内容</div>
            <div
              v-if="detailDialog.data.content"
              class="detail-content"
              v-html="processContent(detailDialog.data.content)"
            />
            <div v-else class="detail-content detail-empty text-tertiary">
              （无详情内容）
            </div>
          </div>
        </template>

        <!-- 加载失败 / 远程不可达 -->
        <div
          v-else-if="!detailDialog.loading"
          class="detail-empty text-tertiary"
        >
          详情获取失败（远程接口不可达或返回为空）
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import StatCard from "@/components/StatCard.vue";
import ProgressBar from "@/components/ProgressBar.vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Edit, Refresh, Delete } from "@element-plus/icons-vue";
import {
  fetchProjectStat,
  fetchProjectActive,
  fetchProjectCommits,
  createProject,
  updateProject,
  deleteProject,
  syncProject
} from "@/api/project";
import {
  fetchRequirements,
  fetchRequirementMilestones,
  fetchRequirementDetail,
  setRequirementAuthorization,
  getRequirementAuthStatus,
  type Requirement,
  type RequirementMilestone,
  type RequirementDetail,
  type RequirementAuthStatus
} from "@/api/requirement";

defineOptions({ name: "ProjectView" });

const stat = ref<any>({});
const active = ref<any[]>([]);
const milestones = ref<RequirementMilestone[]>([]);
const commits = ref<any[]>([]);
const requirements = ref<Requirement[]>([]);

const reqSprint = ref<string>("");
const reqStatus = ref<string>("");
const reqKeyword = ref<string>("");
let reqKeywordTimer: ReturnType<typeof setTimeout> | null = null;

/** authorization 状态（前端动态 token + 后端配置兜底 token） */
const authStatus = ref<RequirementAuthStatus | null>(null);
const authInput = ref<string>("");
const authSaving = ref(false);
/** 鉴权是否已过期（developTask/query 或 sprint/query 返回 401/403 时为 true） */
const authExpired = ref(false);

/** authorization 状态文案 */
const authStatusText = computed(() => {
  if (!authStatus.value || authStatus.value.source === "none") {
    return "未设置 authorization";
  }
  if (authStatus.value.source === "dynamic") {
    return "已设置动态 authorization（前端输入）";
  }
  return "使用配置文件兜底 authorization";
});

/** authorization 状态样式类 */
const authStatusCls = computed(() => {
  if (!authStatus.value || authStatus.value.source === "none") return "auth-none";
  if (authExpired.value) return "auth-expired";
  if (authStatus.value.source === "dynamic") return "auth-dynamic";
  return "auth-fallback";
});

const dialogVisible = ref(false);
const submitting = ref(false);
const syncingId = ref<number | null>(null);
const deletingId = ref<number | null>(null);
const editingId = ref<number | null>(null);

/** 需求详情弹窗状态 */
const detailDialog = ref<{
  visible: boolean;
  loading: boolean;
  data: RequirementDetail | null;
  sourceId: string | null;
}>({
  visible: false,
  loading: false,
  data: null,
  sourceId: null
});

const form = ref<{
  id: number | null;
  name: string;
  tech: string;
  description: string;
  gitUrl: string;
  gitUsername: string;
  gitPassword: string;
  gitBranch: string;
  gitPlatform: string;
}>({
  id: null,
  name: "",
  tech: "",
  description: "",
  gitUrl: "",
  gitUsername: "",
  gitPassword: "",
  gitBranch: "main",
  gitPlatform: "generic"
});

const gitUrlPlaceholder = computed(() => {
  switch (form.value.gitPlatform) {
    case "github":
      return "https://github.com/owner/repo.git";
    case "gitlab":
      return "http(s)://gitlab.com/group/project.git 或自建实例";
    default:
      return "任意 git 仓库地址（含 .git）";
  }
});
const usernamePlaceholder = computed(() => {
  switch (form.value.gitPlatform) {
    case "github":
      return "GitHub 用户名（可选，仅作记录）";
    case "gitlab":
      return "GitLab 用户名（可选，仅作记录）";
    default:
      return "git 用户名（通用鉴权）";
  }
});
const passwordPlaceholder = computed(() => {
  switch (form.value.gitPlatform) {
    case "github":
      return "GitHub Personal Access Token（PAT）";
    case "gitlab":
      return "GitLab Personal Access Token";
    default:
      return "密码或 Access Token";
  }
});

async function load() {
  // 先拉取 authorization 状态（用于展示状态 + 判断是否需要提示用户输入）
  try {
    const a = await getRequirementAuthStatus();
    authStatus.value = a.data;
  } catch {
    /* 拉取状态失败不阻塞主流程 */
  }
  try {
    const [s, a, m, c] = await Promise.all([
      fetchProjectStat(),
      fetchProjectActive(),
      fetchRequirementMilestones(),
      fetchProjectCommits()
    ]);
    stat.value = s.data;
    active.value = a.data;
    milestones.value = m.data || [];
    commits.value = c.data;
  } catch {
    /* 拦截器已提示 */
  }
  // 需求列表单独加载，避免里程碑失败影响主流程
  await loadRequirements();
}

/** 加载"我的需求"列表（带当前筛选条件） */
async function loadRequirements() {
  try {
    const r = await fetchRequirements(reqSprint.value, reqStatus.value, reqKeyword.value);
    requirements.value = r.data || [];
    // 有数据 → 鉴权未过期；无数据 → 标记为"可能鉴权过期"（让 UI 提示输入）
    authExpired.value = requirements.value.length === 0
      && (!authStatus.value || authStatus.value.hasEffectiveToken);
  } catch {
    /* 拦截器已提示 */
    requirements.value = [];
    authExpired.value = true;
  }
}

/** 保存 authorization 并刷新"我的需求"+"里程碑进度" */
async function onSaveAuthorization() {
  const token = authInput.value.trim();
  if (!token) {
    ElMessage.warning("请粘贴最新 authorization（JWT）");
    return;
  }
  authSaving.value = true;
  try {
    console.log("[onSaveAuthorization] 开始设置 authorization, token 长度:", token.length);
    const r = await setRequirementAuthorization(token);
    authStatus.value = r.data;
    authExpired.value = false;
    authInput.value = "";
    ElMessage.success("authorization 已保存，正在刷新数据");
    console.log("[onSaveAuthorization] authorization 已设置成功，source=", r.data?.source, "，开始刷新需求列表 + 里程碑进度");
    // 用 allSettled 让两个请求各自独立完成，互不影响（避免里程碑失败导致需求列表也不刷新）
    const [reqResult, msResult] = await Promise.allSettled([
      fetchRequirements(reqSprint.value, reqStatus.value, reqKeyword.value),
      fetchRequirementMilestones()
    ]);
    if (reqResult.status === "fulfilled") {
      requirements.value = reqResult.value.data || [];
      console.log("[onSaveAuthorization] 需求列表刷新成功，数量:", requirements.value.length);
    } else {
      console.error("[onSaveAuthorization] 需求列表刷新失败:", reqResult.reason);
      requirements.value = [];
    }
    if (msResult.status === "fulfilled") {
      milestones.value = msResult.value.data || [];
      console.log("[onSaveAuthorization] 里程碑进度刷新成功，数量:", milestones.value.length);
    } else {
      console.error("[onSaveAuthorization] 里程碑进度刷新失败:", msResult.reason);
    }
    authExpired.value = requirements.value.length === 0;
  } catch (e) {
    console.error("[onSaveAuthorization] 整体失败:", e);
    /* 拦截器已提示 */
  } finally {
    authSaving.value = false;
  }
}

function onReqFilterChange() {
  loadRequirements();
}

/** 关键词输入 250ms 防抖 */
function onReqKeywordInput() {
  if (reqKeywordTimer) clearTimeout(reqKeywordTimer);
  reqKeywordTimer = setTimeout(() => loadRequirements(), 250);
}

function resetForm() {
  form.value = {
    id: null,
    name: "",
    tech: "",
    description: "",
    gitUrl: "",
    gitUsername: "",
    gitPassword: "",
    gitBranch: "main",
    gitPlatform: "generic"
  };
}

function openCreateDialog() {
  resetForm();
  dialogVisible.value = true;
}

function openEditDialog(p: any) {
  form.value = {
    id: p.id,
    name: p.name || "",
    tech: p.tech || "",
    description: p.description || "",
    gitUrl: p.gitUrl || "",
    gitUsername: p.gitUsername || "",
    // 编辑时密码字段留空，表示不修改
    gitPassword: "",
    gitBranch: p.gitBranch || "main",
    gitPlatform: p.gitPlatform || "generic"
  };
  dialogVisible.value = true;
}

async function submitForm() {
  if (!form.value.name.trim()) {
    ElMessage.warning("请填写项目名");
    return;
  }
  submitting.value = true;
  try {
    if (form.value.id) {
      // 编辑模式
      const payload: Record<string, any> = {
        name: form.value.name,
        tech: form.value.tech,
        description: form.value.description,
        gitUrl: form.value.gitUrl,
        gitUsername: form.value.gitUsername,
        gitBranch: form.value.gitBranch,
        gitPlatform: form.value.gitPlatform
      };
      // 密码留空表示不修改，前端不传该字段，后端 isFieldChanged 不会触发
      if (form.value.gitPassword) {
        payload.gitPassword = form.value.gitPassword;
      }
      await updateProject(form.value.id, payload);
      ElMessage.success(
        form.value.gitUrl
          ? "项目已更新，后台正在异步重新同步 Git 数据"
          : "项目已更新"
      );
      dialogVisible.value = false;
      await load();
      if (form.value.gitUrl) {
        setTimeout(() => { load(); }, 4000);
      }
    } else {
      // 新建模式
      await createProject({
        name: form.value.name,
        tech: form.value.tech,
        description: form.value.description,
        gitUrl: form.value.gitUrl,
        gitUsername: form.value.gitUsername,
        gitPassword: form.value.gitPassword,
        gitBranch: form.value.gitBranch,
        gitPlatform: form.value.gitPlatform
      });
      ElMessage.success(
        form.value.gitUrl
          ? "项目已创建，后台正在异步同步 Git 数据，稍后刷新查看"
          : "项目已创建"
      );
      dialogVisible.value = false;
      await load();
      if (form.value.gitUrl) {
        setTimeout(() => { load(); }, 4000);
      }
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false;
  }
}

async function onSync(p: any) {
  syncingId.value = p.id;
  try {
    await syncProject(p.id);
    ElMessage.info("同步已开始，请稍后刷新查看结果");
    setTimeout(() => { load(); }, 3000);
    setTimeout(() => { load(); }, 8000);
  } catch {
    /* 拦截器已提示 */
  } finally {
    syncingId.value = null;
  }
}

async function onDelete(p: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除项目「${p.name}」？该项目下的提交/合并/发布记录将一并清除，且不再参与统计。`,
      "删除项目",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  deletingId.value = p.id;
  try {
    await deleteProject(p.id);
    ElMessage.success("已删除");
    await load();
  } catch {
    /* 拦截器已提示 */
  } finally {
    deletingId.value = null;
  }
}

function dotCls(p: any) {
  if (p.status === "pending") return "dot-warning";
  return "dot-positive";
}

function milestoneColor(p: number) {
  if (p >= 100) return "#2dd4bf";
  if (p >= 80) return "#a78bfa";
  return "#fbbf24";
}

/** 里程碑标签：sprint + 日期（如 CDS20261025 · 10-25） */
function milestoneLabel(m: RequirementMilestone): string {
  if (!m.dueDate) return m.name;
  const d = String(m.dueDate).replace("T", " ").slice(0, 10);
  // d 形如 2026-10-25
  const mm = d.slice(5, 7);
  const dd = d.slice(8, 10);
  return `${m.name} · ${mm}-${dd}`;
}

/** 需求任务状态文案（按用户提供的枚举） */
function reqStatusText(s?: string): string {
  switch (s) {
    case "todo":
      return "待办";
    case "doing":
      return "进行中";
    case "testing":
      return "提测";
    case "pending":
      return "待发版";
    case "done":
      return "完成";
    case "cancel":
      return "已取消";
    default:
      return s || "—";
  }
}

/** 需求任务状态标签样式 */
function reqStatusCls(s?: string): string {
  switch (s) {
    case "todo":
      return "st-todo";
    case "doing":
      return "st-doing";
    case "testing":
      return "st-testing";
    case "pending":
      return "st-pending";
    case "done":
      return "st-done";
    case "cancel":
      return "st-cancel";
    default:
      return "st-todo";
  }
}

/**
 * 详情弹窗里的远程原始 status → 本地状态（用于状态标签渲染）。
 * 后端列表已映射过，但详情接口返回的是原始 status（open/in_progress/in_test/for_release/developed）。
 */
function mapRemoteStatusToLocal(remote?: string): string {
  switch (remote) {
    case "open":
      return "todo";
    case "in_progress":
      return "doing";
    case "in_test":
      return "testing";
    case "for_release":
      return "pending";
    case "developed":
      return "done";
    default:
      return "todo";
  }
}

/** 点击需求列表行 → 弹详情 */
async function onReqRowClick(row: Requirement) {
  if (!row.sourceId) {
    ElMessage.warning("该需求无 sourceId，无法查询详情");
    return;
  }
  detailDialog.value = {
    visible: true,
    loading: true,
    data: null,
    sourceId: row.sourceId
  };
  try {
    const r = await fetchRequirementDetail(row.sourceId);
    detailDialog.value.data = (r.data as RequirementDetail) || null;
  } catch (e: any) {
    /* 拦截器已提示 */
    detailDialog.value.data = null;
  } finally {
    detailDialog.value.loading = false;
  }
}

/**
 * 把 content HTML 转为纯文本展示（去除所有标签，只保留文本）。
 *
 * <p>用户要求：详情弹窗 content 中的 HTML 标签都不展示，只展示文本内容。
 * 处理步骤：
 * <ul>
 *   <li>HTML 反转义：&amp; → &，&lt; → <，&gt; → >，&quot; → "，&#39; → '，&nbsp; → 空格</li>
 *   <li>块级标签（</p><br></div><li><tr>）转为换行，保留段落结构</li>
 *   <li>去除所有 <...> 标签</li>
 *   <li>合并多余空行（连续 3+ 换行压成 2 个）</li>
 * </ul>
 * </p>
 */
function stripHtmlToText(content: string | undefined): string {
  if (!content) return "";
  let text = content;
  // HTML 反转义（注意先反转义 &amp;，避免后续替换冲突）
  text = text
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/&apos;/gi, "'")
    .replace(/&nbsp;/gi, " ");
  // 块级标签转为换行（保留段落结构）
  text = text.replace(/<\/(p|div|li|tr|h[1-6]|section|article|header|footer|blockquote)>/gi, "\n");
  text = text.replace(/<br\s*\/?>/gi, "\n");
  // 去除所有 HTML 标签（含 img、a、figure 等）
  text = text.replace(/<[^>]+>/g, "");
  // 合并多余空白（连续 3+ 换行压成 2 个，行首尾空白去除）
  text = text.replace(/[ \t]+\n/g, "\n").replace(/\n{3,}/g, "\n\n").replace(/^\s+|\s+$/g, "");
  return text;
}

/**
 * content HTML 预处理：把所有 img 标签的 src 改为走后端图片代理。
 *
 * <p>原因：第三方需求接口 {@code wetrans.cimc.cn} 的图片资源可能：
 * <ul>
 *   <li>使用相对路径（如 {@code /upload/xxx.jpg}），浏览器基于前端域名请求会 404；</li>
 *   <li>使用绝对 URL 但需要 Authorization 头，浏览器 {@code <img>} 标签无法附加自定义请求头，导致 401。</li>
 * </ul>
 * 统一改为 {@code /api/project/requirements/img-proxy?url=<encoded>}，由后端用 Authorization 头拉取后透传字节流。</p>
 *
 * <p><b>关键</b>：HTML 中 src 属性里的 {@code &} 通常被转义为 {@code &amp;}（如 OSS 带 Signature 的 URL），
 * 必须在 URL 编码前先 HTML 反转义，否则后端拿到的 URL 含 {@code &amp;} 而非 {@code &}，
 * 远程图片服务端会因签名不匹配返回 403/404。</p>
 */
function processContent(content: string | undefined): string {
  if (!content) return "";
  // 远程接口域名（用于补全相对路径）
  const remoteBase = "https://wetrans.cimc.cn";
  // 后端图片代理端点（vite 代理 /api → http://localhost:8080）
  const proxyBase = "/api/project/requirements/img-proxy?url=";

  // HTML 反转义：&amp; → &，&lt; → <，&gt; → >，&quot; → "，&#39; → '
  const htmlUnescape = (s: string): string =>
    s
      .replace(/&amp;/gi, "&")
      .replace(/&lt;/gi, "<")
      .replace(/&gt;/gi, ">")
      .replace(/&quot;/gi, '"')
      .replace(/&#39;/gi, "'");

  let processed = content;

  // 1) src="/xxx"（绝对路径但相对域名）→ 补全为 https://wetrans.cimc.cn/xxx 再走代理
  processed = processed.replace(
    /src=(["'])\/(?!\/)([^"'\s>]+)\1/gi,
    (_m, q, path) => `src=${q}${proxyBase}${encodeURIComponent(remoteBase + "/" + htmlUnescape(path))}${q}`
  );

  // 2) src="https://wetrans.cimc.cn/xxx" 或 src="http(s)://其他域/xxx"（绝对 URL，可能需要鉴权）→ 直接走代理
  //    但排除已经是代理地址的，避免重复编码
  processed = processed.replace(
    /src=(["'])(https?:\/\/(?!localhost)[^"'\s>]+)\1/gi,
    (_m, q, url) =>
      url.startsWith(proxyBase) ? _m : `src=${q}${proxyBase}${encodeURIComponent(htmlUnescape(url))}${q}`
  );

  // 3) src="xxx"（不以 / 或 http 开头的相对路径，少见）→ 拼接到 wetrans.cimc.cn 根
  processed = processed.replace(
    /src=(["'])((?!\/|https?:)[^"'\s>]+)\1/gi,
    (_m, q, path) => `src=${q}${proxyBase}${encodeURIComponent(remoteBase + "/" + htmlUnescape(path))}${q}`
  );

  return processed;
}

/** 详情字段时间格式化：远程返回毫秒时间戳或 ISO 字符串，统一转为 yyyy-MM-dd HH:mm */
function formatTime(v: any): string {
  if (v == null || v === "") return "—";
  // 毫秒时间戳
  if (typeof v === "number" || /^\d+$/.test(String(v))) {
    const d = new Date(Number(v));
    if (!isNaN(d.getTime())) {
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
    }
  }
  // ISO 字符串或其他
  const s = String(v);
  if (s.length >= 16) return s.substring(0, 16).replace("T", " ");
  return s;
}

function commitColor(type?: string) {
  switch (type) {
    case "feat":
      return "#a78bfa";
    case "fix":
      return "#f87171";
    case "chore":
      return "#6b7280";
    case "refactor":
      return "#2dd4bf";
    case "docs":
      return "#c4b5fd";
    case "merge":
      return "#fbbf24";
    default:
      return "#6b7280";
  }
}

function shortenUrl(url: string) {
  if (!url) return "";
  return url.length > 36 ? url.slice(0, 34) + "…" : url;
}

/** 把后端返回的 lastSyncAt（yyyy-MM-dd HH:mm:ss）转换为相对时间文案 */
function pushTimeAgo(t: string | null | undefined) {
  if (!t) return "未同步";
  const normalized = (t as string).replace("T", " ");
  const ts = new Date(normalized.replace(/-/g, "/")).getTime();
  if (Number.isNaN(ts)) return "未同步";
  const diffMs = Date.now() - ts;
  if (diffMs < 0) return "刚刚";
  const min = Math.floor(diffMs / 60000);
  if (min < 1) return "刚刚";
  if (min < 60) return `${min} 分钟前`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour} 小时前`;
  const day = Math.floor(hour / 24);
  if (day < 30) return `${day} 天前`;
  const month = Math.floor(day / 30);
  return `${month} 个月前`;
}

/** 同步状态标签文案 */
function syncTagText(p: any) {
  switch (p.syncStatus) {
    case "success":
      return "已同步";
    case "failed":
      return "同步失败";
    case "syncing":
      return "同步中";
    case "pending":
    default:
      return "未同步";
  }
}

/** 同步状态标签 tooltip */
function syncTagTip(p: any) {
  switch (p.syncStatus) {
    case "success":
      return "Git 数据已成功拉取";
    case "failed":
      return "Git 同步失败，请检查凭据是否为有效的 Access Token";
    case "syncing":
      return "后台正在拉取 Git 数据";
    case "pending":
    default:
      return "项目尚未触发过 Git 同步";
  }
}

/** 同步状态标签样式类 */
function syncTagCls(p: any) {
  switch (p.syncStatus) {
    case "success":
      return "sync-success";
    case "failed":
      return "sync-failed";
    case "syncing":
      return "sync-syncing";
    default:
      return "sync-pending";
  }
}

onMounted(load);
</script>

<style scoped lang="scss">
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}

.dual-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  align-items: start;
}

/* 活跃项目占左列两行，高度匹配右侧"我的需求 + 里程碑进度"两张卡片 */
.dual-row > .ld-card:first-child {
  grid-row: span 2;
}

/* 最近提交活动在最下方跨两列 */
.dual-row > .ld-card:last-child {
  grid-column: 1 / -1;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}
.empty {
  color: $text-tertiary;
  font-size: 13px;
  padding: 12px 0;
}

.proj-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 8px;
  border-bottom: 1px solid $border-subtle;
  border-radius: 8px;
  transition: background 0.2s;
  &:last-child {
    border-bottom: none;
  }
  &:hover {
    background: rgba(255, 255, 255, 0.04);
    .proj-actions {
      opacity: 1;
      transform: translateX(0);
      pointer-events: auto;
    }
  }
}
.color-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 6px;
}
.dot-positive {
  background: $color-positive;
}
.dot-warning {
  background: $color-warning;
}
.proj-info {
  flex: 1;
  min-width: 0;
}
.proj-name {
  font-size: 14px;
  color: $text-primary;
  font-weight: 500;
}
.tech-tag {
  display: inline-block;
  padding: 1px 8px;
  margin-left: 6px;
  font-size: 11px;
  background: rgba(196, 181, 253, 0.12);
  color: $accent-light;
  border-radius: 8px;
}
.branch-tag {
  display: inline-block;
  padding: 1px 8px;
  margin-left: 6px;
  font-size: 11px;
  background: rgba(44, 212, 191, 0.12);
  color: #2dd4bf;
  border-radius: 8px;
}
.proj-desc {
  font-size: 12px;
  color: $text-secondary;
  margin-top: 3px;
}
.proj-git {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.git-stat {
  font-size: 11px;
  color: $text-tertiary;
  background: rgba(167, 139, 250, 0.08);
  padding: 1px 6px;
  border-radius: 4px;
}
.git-stat.push-time {
  background: rgba(255, 255, 255, 0.06);
  color: $text-secondary;
}
// 同步状态标签：成功绿、失败红、同步中灰、未同步灰
.sync-tag.sync-success {
  background: rgba(45, 212, 191, 0.14);
  color: #2dd4bf;
}
.sync-tag.sync-failed {
  background: rgba(248, 113, 113, 0.16);
  color: #f87171;
  cursor: help;
}
.sync-tag.sync-syncing {
  background: rgba(251, 191, 36, 0.14);
  color: #fbbf24;
}
.sync-tag.sync-pending {
  background: rgba(255, 255, 255, 0.06);
  color: $text-tertiary;
}
.proj-right {
  text-align: right;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}
.proj-time {
  font-size: 11px;
  white-space: nowrap;
}
.proj-actions {
  display: flex;
  gap: 6px;
  opacity: 0;
  transform: translateX(6px);
  pointer-events: none;
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.m-note {
  margin-top: 10px;
  font-size: 12px;
}

/* ========== 我的需求卡片 ========== */
.req-card {
  /* el-table 在玻璃卡片内更紧凑 */
}
.req-filter {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.req-tip {
  font-size: 11px;
  margin: 4px 0 10px;
  padding-left: 1px;
}

/* authorization 输入区：wetrans.cimc.cn/gateway/ 接口鉴权每日可能过期 */
.auth-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 8px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  flex-wrap: wrap;
}
.auth-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  flex-shrink: 0;
  font-weight: 500;
}
.auth-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.auth-status.auth-dynamic {
  background: rgba(45, 212, 191, 0.14);
  color: #2dd4bf;
}
.auth-status.auth-dynamic .auth-dot {
  background: #2dd4bf;
  box-shadow: 0 0 6px #2dd4bf;
}
.auth-status.auth-fallback {
  background: rgba(251, 191, 36, 0.14);
  color: #fbbf24;
}
.auth-status.auth-fallback .auth-dot {
  background: #fbbf24;
}
.auth-status.auth-expired {
  background: rgba(248, 113, 113, 0.16);
  color: #f87171;
}
.auth-status.auth-expired .auth-dot {
  background: #f87171;
  animation: auth-pulse 1.4s ease-in-out infinite;
}
.auth-status.auth-none {
  background: rgba(255, 255, 255, 0.06);
  color: $text-tertiary;
}
.auth-status.auth-none .auth-dot {
  background: $text-tertiary;
}
@keyframes auth-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.4); }
}
.auth-input {
  flex: 1;
  min-width: 240px;
}
/* auth 输入框玻璃风格适配 */
.auth-input :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.04) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08) inset !important;
  border-radius: 6px;
}
.auth-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(196, 181, 253, 0.35) inset !important;
}
.auth-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px rgba(167, 139, 250, 0.5) inset,
    0 0 0 3px rgba(167, 139, 250, 0.12) inset !important;
}
.auth-input :deep(.el-input__inner) {
  color: var(--ld-text-primary, #f5f3ff) !important;
  font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}
.auth-input :deep(.el-input__inner::placeholder) {
  color: var(--ld-text-tertiary, #786f99) !important;
}

/* 鉴权过期 / 空数据提示（需求列表 + 里程碑进度） */
.req-empty-hint,
.m-empty-hint {
  padding: 18px 12px;
  text-align: center;
  font-size: 13px;
  color: #f87171;
  background: rgba(248, 113, 113, 0.06);
  border: 1px dashed rgba(248, 113, 113, 0.3);
  border-radius: 8px;
  margin: 8px 0;
}
.m-empty-hint {
  margin: 4px 0;
}
/* el-table 在玻璃卡片中：彻底清掉所有白底，背景跟随卡片玻璃质感 */
.req-table {
  background: transparent !important;
  font-size: 12px;
  /* CSS 变量给 el-table 内部组件用 */
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-expanded-cell-bg-color: transparent;
  --el-table-header-bg-color: rgba(167, 139, 250, 0.08);
  --el-table-header-text-color: var(--ld-text-secondary, rgba(255, 255, 255, 0.65));
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.04);
  --el-table-current-row-bg-color: rgba(255, 255, 255, 0.04);
  --el-table-border-color: rgba(255, 255, 255, 0.06);
  --el-table-text-color: var(--ld-text-primary, rgba(255, 255, 255, 0.92));
  /* empty-block / fill 等场景的兜底填充色 */
  --el-fill-color: transparent;
  --el-fill-color-light: transparent;
  --el-fill-color-lighter: transparent;
  --el-bg-color: transparent;
  --el-bg-color-page: transparent;
}
/* 彻底清掉所有可能的白底元素 */
.req-table :deep(.el-table__inner-wrapper),
.req-table :deep(.el-table__body-wrapper),
.req-table :deep(.el-table__header-wrapper),
.req-table :deep(.el-table__footer-wrapper),
.req-table :deep(.el-table__body),
.req-table :deep(.el-table__header),
.req-table :deep(.el-table__footer),
.req-table :deep(.el-table__cell),
.req-table :deep(.el-table__row),
.req-table :deep(.el-table__empty-block),
.req-table :deep(.el-table__empty-text),
.req-table :deep(.el-table__inner-wrapper::before),
.req-table :deep(.el-scrollbar),
.req-table :deep(.el-scrollbar__wrap),
.req-table :deep(.el-scrollbar__view) {
  background: transparent !important;
  background-color: transparent !important;
}
/* 表头轻微紫色高亮 */
.req-table :deep(th.el-table__cell) {
  background: rgba(167, 139, 250, 0.08) !important;
  background-color: rgba(167, 139, 250, 0.08) !important;
  font-weight: 600;
}
/* 行 hover 微高亮（保持玻璃质感） */
.req-table :deep(.el-table__body tr:hover > td.el-table__cell),
.req-table :deep(.el-table__body-wrapper .el-table__row:hover > td.el-table__cell) {
  background: rgba(255, 255, 255, 0.04) !important;
  background-color: rgba(255, 255, 255, 0.04) !important;
}
/* sticky 固定列（Sprint 列）也要透明，避免 fixed 列下方有白底 */
.req-table :deep(.el-table__body .el-table__row td.el-table__cell.is-hidden),
.req-table :deep(.el-table__body .el-table__row th.el-table__cell.is-hidden) {
  background: transparent !important;
  background-color: transparent !important;
}
/* 边框颜色淡化（融入玻璃） */
.req-table :deep(td.el-table__cell),
.req-table :deep(th.el-table__cell) {
  border-bottom-color: rgba(255, 255, 255, 0.06) !important;
  border-right-color: transparent !important;
}
/* 滚动条样式适配深色玻璃质感 */
.req-table :deep(.el-table__body-wrapper) {
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.15) transparent;
}
.req-table :deep(.el-table__body-wrapper)::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.req-table :deep(.el-table__body-wrapper)::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 3px;
}
.req-table :deep(.el-table__body-wrapper)::-webkit-scrollbar-track {
  background: transparent;
}
/* 任务详情单元格：单行省略号（即使未截断，hover 时也通过 tooltip 展示全部内容） */
.task-detail-cell {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

/* tooltip 自定义玻璃风格（popper-class 全局类，需用 :global 跨 scoped 边界） */
:global(.req-task-tooltip.el-popper) {
  background: rgba(28, 28, 36, 0.92) !important;
  backdrop-filter: blur(14px) saturate(180%);
  -webkit-backdrop-filter: blur(14px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  color: rgba(255, 255, 255, 0.92) !important;
  font-size: 12px;
  line-height: 1.6;
  max-width: 520px;
  padding: 8px 12px;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  word-break: break-all;
}
/* tooltip 小三角箭头跟随玻璃质感 */
:global(.req-task-tooltip.el-popper[data-popper-placement^=top] .el-popper__arrow::before),
:global(.req-task-tooltip.el-popper[data-popper-placement^=bottom] .el-popper__arrow::before) {
  background: rgba(28, 28, 36, 0.92) !important;
  border-color: rgba(255, 255, 255, 0.08) !important;
}

.sprint-tag {
  display: inline-block;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 8px;
  background: rgba(167, 139, 250, 0.16);
  color: $accent-light;
  letter-spacing: 0.3px;
}
.person-day {
  font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  color: $text-primary;
}
.status-tag {
  display: inline-block;
  padding: 1px 8px;
  font-size: 11px;
  border-radius: 8px;
  line-height: 1.5;
}
.status-tag.st-todo {
  background: rgba(255, 255, 255, 0.06);
  color: $text-tertiary;
}
.status-tag.st-doing {
  background: rgba(251, 191, 36, 0.16);
  color: #fbbf24;
}
.status-tag.st-testing {
  background: rgba(96, 165, 250, 0.16);
  color: #60a5fa;
}
.status-tag.st-pending {
  background: rgba(167, 139, 250, 0.16);
  color: #a78bfa;
}
.status-tag.st-done {
  background: rgba(45, 212, 191, 0.16);
  color: #2dd4bf;
}
.status-tag.st-cancel {
  background: rgba(248, 113, 113, 0.14);
  color: #f87171;
  text-decoration: line-through;
}

.commit-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid $border-subtle;
  &:last-child {
    border-bottom: none;
  }
}
.commit-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.commit-info {
  flex: 1;
  min-width: 0;
}
.commit-author {
  font-size: 13px;
  color: $text-primary;
}
.commit-proj-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  font-size: 11px;
  background: rgba(167, 139, 250, 0.14);
  color: $accent-light;
  border-radius: 8px;
}
.commit-msg {
  font-size: 12px;
  color: $text-secondary;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.commit-time {
  font-size: 11px;
  flex-shrink: 0;
}

:deep(.el-divider__text) {
  font-size: 12px;
  color: $text-tertiary;
}

/* ========== 工程统一弹窗玻璃风格 ==========
 * 与 .ld-card 一致的玻璃质感：
 *   - background: $bg-card (rgba(255,255,255,0.05))
 *   - border: 1px solid $border-glass (rgba(255,255,255,0.12))
 *   - border-radius: $radius-card (18px)
 *   - box-shadow: $shadow-glass (0 8px 32px ... + inset 0 1px 0 ...)
 *   - backdrop-filter: blur(20px) saturate(180%)
 * 用于所有 el-dialog（新建/编辑项目 + 需求详情），保持工程风格一致。
 */
/* el-overlay 遮罩：与 bookmark .modal-overlay 一致的黑底 + 轻模糊
 * modal-class 是 el-dialog 提供的属性，会附加到 .el-overlay 上（无需 :has 兼容性顾虑） */
:global(.ld-overlay) {
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

/* 弹窗容器：紫黑玻璃质感（与系统风格一致） */
/* 覆盖 el-dialog 默认 CSS 变量（默认为白色 --el-bg-color / --el-dialog-bg-color），
 * 改为紫黑色，避免内部元素继承白色变量导致白底 */
.ld-dialog :deep(.el-dialog),
.ld-dialog.el-dialog,
.ld-dialog :deep(.el-overlay-dialog) {
  --el-dialog-bg-color: rgba(28, 28, 36, 0.78);
  --el-bg-color: rgba(28, 28, 36, 0.78);
  --el-bg-color-page: transparent;
  --el-mask-color: transparent;
}
.ld-dialog :deep(.el-dialog) {
  /* 紫黑半透明底色（系统风格），文字浅色清晰可见 */
  background: rgba(28, 28, 36, 0.78) !important;
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 18px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25), inset 0 1px 0 rgba(255, 255, 255, 0.12);
  overflow: hidden;
  /* 关键：用 background-color + background 双重声明，确保覆盖 el-dialog 内联样式 */
  background-color: rgba(28, 28, 36, 0.78) !important;
  background-image: none !important;
}
/* el-dialog 内部元素背景透明，跟随紫黑底色（避免各自带白底） */
.ld-dialog :deep(.el-dialog__header),
.ld-dialog :deep(.el-dialog__body),
.ld-dialog :deep(.el-dialog__footer),
.ld-dialog :deep(.el-dialog__headerbtn),
.ld-dialog :deep(.el-dialog__content),
.ld-dialog :deep(.el-dialog__container) {
  background: transparent !important;
  background-color: transparent !important;
}
/* 弹窗标题栏 */
.ld-dialog :deep(.el-dialog__header) {
  padding: 16px 22px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  margin-right: 0;
  background: rgba(255, 255, 255, 0.02);
}
.ld-dialog :deep(.el-dialog__title) {
  font-size: 15px;
  font-weight: 600;
  color: var(--ld-text-primary, #f5f3ff);
  letter-spacing: 0.3px;
}
.ld-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: var(--ld-text-secondary, #b8b0d4);
  font-size: 18px;
  transition: color 0.2s;
}
.ld-dialog :deep(.el-dialog__headerbtn:hover .el-dialog__close) {
  color: var(--ld-text-primary, #f5f3ff);
}
/* 弹窗主体 */
.ld-dialog :deep(.el-dialog__body) {
  padding: 20px 22px 24px;
  color: var(--ld-text-primary, #f5f3ff);
}
/* 弹窗底部（按钮区） */
.ld-dialog :deep(.el-dialog__footer) {
  padding: 12px 22px 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(255, 255, 255, 0.02);
}
/* 弹窗内的 el-form 元素玻璃风格适配 */
.ld-dialog :deep(.el-form-item__label) {
  color: var(--ld-text-secondary, #b8b0d4);
  font-size: 13px;
}
.ld-dialog :deep(.el-input__wrapper),
.ld-dialog :deep(.el-textarea__inner) {
  background: rgba(255, 255, 255, 0.04) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08) inset !important;
  border-radius: 8px;
  transition: box-shadow 0.2s;
}
.ld-dialog :deep(.el-input__wrapper:hover),
.ld-dialog :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px rgba(196, 181, 253, 0.35) inset !important;
}
.ld-dialog :deep(.el-input__wrapper.is-focus),
.ld-dialog :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px rgba(167, 139, 250, 0.5) inset,
    0 0 0 3px rgba(167, 139, 250, 0.12) inset !important;
}
.ld-dialog :deep(.el-input__inner),
.ld-dialog :deep(.el-textarea__inner) {
  color: var(--ld-text-primary, #f5f3ff) !important;
}
.ld-dialog :deep(.el-input__inner::placeholder),
.ld-dialog :deep(.el-textarea__inner::placeholder) {
  color: var(--ld-text-tertiary, #786f99) !important;
}
/* el-divider 在弹窗中也要玻璃风格 */
.ld-dialog :deep(.el-divider) {
  border-color: rgba(255, 255, 255, 0.08);
}
.ld-dialog :deep(.el-divider__text) {
  background: transparent;
  color: var(--ld-text-tertiary, #786f99);
  font-size: 12px;
  padding: 0 10px;
}
/* el-radio-button 玻璃风格适配 */
.ld-dialog :deep(.el-radio-button__inner) {
  background: rgba(255, 255, 255, 0.04) !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
  color: var(--ld-text-secondary, #b8b0d4) !important;
}
.ld-dialog :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: rgba(167, 139, 250, 0.18) !important;
  border-color: rgba(167, 139, 250, 0.5) !important;
  color: #c4b5fd !important;
  box-shadow: 0 0 0 1px rgba(167, 139, 250, 0.35) inset !important;
}
/* el-button 主按钮微紫色光感 */
.ld-dialog :deep(.el-button--primary) {
  background: rgba(167, 139, 250, 0.22) !important;
  border-color: rgba(167, 139, 250, 0.5) !important;
  color: #f5f3ff !important;
}
.ld-dialog :deep(.el-button--primary:hover) {
  background: rgba(167, 139, 250, 0.32) !important;
  border-color: rgba(196, 181, 253, 0.65) !important;
}
.ld-dialog :deep(.el-button--default) {
  background: rgba(255, 255, 255, 0.05) !important;
  border-color: rgba(255, 255, 255, 0.12) !important;
  color: var(--ld-text-secondary, #b8b0d4) !important;
}
.ld-dialog :deep(.el-button--default:hover) {
  background: rgba(255, 255, 255, 0.1) !important;
  border-color: rgba(255, 255, 255, 0.2) !important;
  color: var(--ld-text-primary, #f5f3ff) !important;
}
/* loading 蒙层透明，跟随弹窗背景 */
.ld-dialog :deep(.el-loading-mask) {
  background: transparent;
}

/* ========== 需求详情弹窗内容样式（沿用 ld-dialog 容器，仅定义内容布局） ========== */
.req-detail-body {
  min-height: 200px;
}
.detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--ld-text-primary, rgba(255, 255, 255, 0.92));
}
.detail-task-no {
  font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  background: rgba(167, 139, 250, 0.14);
  color: #a78bfa;
  padding: 2px 8px;
  border-radius: 6px;
  letter-spacing: 0.3px;
}
.detail-title-text {
  flex: 1;
  word-break: break-all;
}
/* 元信息网格 */
.detail-meta {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 18px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  margin-bottom: 18px;
}
.meta-item {
  display: flex;
  gap: 10px;
  font-size: 12px;
  line-height: 1.6;
}
.meta-label {
  flex-shrink: 0;
  width: 64px;
  color: var(--ld-text-tertiary, rgba(255, 255, 255, 0.45));
}
.meta-value {
  flex: 1;
  word-break: break-all;
  color: var(--ld-text-secondary, rgba(255, 255, 255, 0.7));
}
/* 详情正文区 */
.detail-section {
  margin-top: 6px;
}
.detail-section + .detail-section {
  margin-top: 14px;
}
.detail-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--ld-text-tertiary, rgba(255, 255, 255, 0.45));
  margin-bottom: 8px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
/* 详情中的"标题"独立展示（区别于弹窗顶部 taskNo + title + status 的紧凑标题区） */
.detail-title-display {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.65;
  color: var(--ld-text-primary, rgba(255, 255, 255, 0.92));
  word-break: break-word;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
}
.detail-content {
  font-size: 13px;
  line-height: 1.7;
  color: var(--ld-text-primary, rgba(255, 255, 255, 0.88));
  word-break: break-word;
  /* 内容容器透明，跟随弹窗玻璃质感（避免白底） */
  background: transparent !important;
  padding: 4px 0;
}
/* v-html 渲染的 HTML 元素玻璃风格适配（背景全部透明，避免白底） */
.detail-content :deep(p) {
  margin: 6px 0;
  background: transparent !important;
}
.detail-content :deep(ol),
.detail-content :deep(ul) {
  margin: 6px 0;
  padding-left: 22px;
  background: transparent !important;
}
.detail-content :deep(li) {
  margin: 4px 0;
  background: transparent !important;
}
/* li 内嵌套的 ol（二级列表）缩进 */
.detail-content :deep(li ol),
.detail-content :deep(li ul) {
  margin: 4px 0 4px 0;
}
.detail-content :deep(figure) {
  margin: 10px 0;
  text-align: center;
  background: transparent !important;
}
.detail-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  /* 图片本身背景用深色（避免透明 PNG 在白底下看不清） */
  background: rgba(0, 0, 0, 0.2);
  padding: 4px;
}
.detail-content :deep(a) {
  color: #a78bfa;
  text-decoration: none;
  border-bottom: 1px dashed rgba(167, 139, 250, 0.4);
}
.detail-content :deep(a:hover) {
  border-bottom-style: solid;
}
/* 块级元素全部透明背景（兜底，防止 content 中带 style 属性的白底） */
.detail-content :deep(div),
.detail-content :deep(span),
.detail-content :deep(article),
.detail-content :deep(section),
.detail-content :deep(blockquote) {
  background: transparent !important;
}
.detail-empty {
  font-size: 13px;
  text-align: center;
  padding: 32px 0;
}
</style>
