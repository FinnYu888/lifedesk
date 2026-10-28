<template>
  <div class="login-page">
    <!-- 背景光斑装饰（与系统液态玻璃风格一致） -->
    <div class="orb orb-a" />
    <div class="orb orb-b" />
    <div class="orb orb-c" />

    <div class="login-card">
      <div class="brand">
        <div class="brand-icon">
          <el-icon :size="30"><HomeFilled /></el-icon>
        </div>
        <h1 class="brand-name">LifeDesk</h1>
        <p class="brand-slogan">一站式个人工作台：项目、任务、健康、英语、音乐与系统监控，尽在一屏。</p>
      </div>

      <el-tabs v-model="mode" class="auth-tabs" stretch>
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <el-form class="auth-form" @submit.prevent="submit">
        <el-form-item>
          <el-input
            v-model="username"
            placeholder="用户名"
            size="large"
            clearable
            autocomplete="username"
          >
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
            autocomplete="current-password"
            @keyup.enter="submit"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-button
          class="submit-btn"
          type="primary"
          size="large"
          :loading="loading"
          native-type="submit"
        >
          {{ mode === "login" ? "登 录" : "注册并登录" }}
        </el-button>
      </el-form>

      <p class="preset-hint">预置账号：俞鹏飞 / finn</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { HomeFilled, User, Lock } from "@element-plus/icons-vue";
import { login, register } from "@/api/auth";
import { useTheme } from "@/composables/useTheme";

defineOptions({ name: "LoginPage" });

const router = useRouter();
// 登录页同样应用所选主题（玻璃/科技/奶白/末日科幻），与系统风格保持一致
useTheme();

const mode = ref<"login" | "register">("login");
const username = ref("");
const password = ref("");
const loading = ref(false);

async function submit() {
  if (!username.value.trim() || !password.value) {
    ElMessage.warning("请输入用户名和密码");
    return;
  }
  loading.value = true;
  try {
    const r: any =
      mode.value === "login"
        ? await login(username.value.trim(), password.value)
        : await register(username.value.trim(), password.value);
    const info = r.data;
    localStorage.setItem("lifedesk-token", info.token);
    localStorage.setItem("lifedesk-user", info.nickname || username.value.trim());
    ElMessage.success(mode.value === "login" ? "欢迎回来" : "注册成功，欢迎加入");
    router.push("/dashboard");
  } catch {
    // 错误提示由 http 拦截器统一弹出
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped lang="scss">
@import "@/styles/variables.scss";

.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100vh;
  padding: 24px;
  overflow: hidden;
  background: $bg-page;
  background-image: $bg-page-grad;
}

/* 背景光斑（呼应玻璃主题的弥散光） */
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.55;
  pointer-events: none;

  &-a {
    top: -120px;
    left: -80px;
    width: 420px;
    height: 420px;
    background: rgba(167, 139, 250, 0.22);
  }

  &-b {
    right: -140px;
    bottom: -100px;
    width: 460px;
    height: 460px;
    background: rgba(45, 212, 191, 0.14);
  }

  &-c {
    top: 40%;
    left: 55%;
    width: 260px;
    height: 260px;
    background: rgba(120, 160, 255, 0.16);
  }
}

.login-card {
  position: relative;
  z-index: 1;
  width: 400px;
  max-width: 92vw;
  padding: 40px 36px 28px;
  border-radius: $radius-card;
  background: $bg-card;
  border: 1px solid $border-glass;
  box-shadow: $shadow-card;
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
}

.brand {
  margin-bottom: 20px;
  text-align: center;

  .brand-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 60px;
    height: 60px;
    margin-bottom: 12px;
    border-radius: 18px;
    color: #fff;
    background: $accent;
    box-shadow: 0 8px 22px rgba(167, 139, 250, 0.35);
  }

  .brand-name {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
    letter-spacing: 0.5px;
    color: $text-primary;
  }

  .brand-slogan {
    margin: 10px auto 0;
    max-width: 300px;
    font-size: 13px;
    line-height: 1.7;
    color: $text-secondary;
  }
}

.auth-tabs {
  margin-bottom: 6px;

  :deep(.el-tabs__item) {
    font-size: 15px;
  }
}

.auth-form {
  margin-top: 12px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
  letter-spacing: 4px;
}

.preset-hint {
  margin: 14px 0 0;
  font-size: 12px;
  text-align: center;
  color: $text-tertiary;
}

/* 窄屏自适应 */
@media (max-width: 480px) {
  .login-card {
    padding: 30px 22px 20px;
  }
}
</style>
