<script setup>
import BrandMark from "@/components/BrandMark.vue";
import AppIcon from "@/components/AppIcon.vue";

defineProps({model: {type: Object, required: true}});
</script>

<template>
  <main class="login-page">
    <div class="login-orb orb-one"></div>
    <div class="login-orb orb-two"></div>
    <div class="login-layout">
      <section class="login-story">
        <BrandMark light/>
        <div class="login-hero-copy">
          <span class="hero-kicker"><AppIcon name="sparkles" :size="16"/>把每一份资料，变成你的成长路径</span>
          <h1>学习有方向，<br><em>求职更有底气。</em></h1>
          <p>上传八股文、课程讲义或技术文档，用 AI 完成理解、复习、面试准备与求职输出。</p>
        </div>

        <div class="login-feature-grid">
          <article>
            <span><AppIcon name="library"/></span>
            <div><strong>沉淀学习资料</strong><small>多知识库统一整理</small></div>
          </article>
          <article>
            <span><AppIcon name="chat"/></span>
            <div><strong>AI 引用式伴学</strong><small>基于资料回答问题</small></div>
          </article>
          <article>
            <span><AppIcon name="briefcase"/></span>
            <div><strong>衔接求职准备</strong><small>简历、面试与岗位分析</small></div>
          </article>
        </div>
      </section>

      <section class="login-card-wrap">
        <form class="login-card" @submit.prevent="model.submitAuthForm">
          <div class="mobile-login-brand"><BrandMark/></div>
          <span class="eyebrow">欢迎来到知途 AI</span>
          <h2>{{ model.authMode === 'login' ? '继续你的成长旅程' : '创建个人成长空间' }}</h2>
          <p class="login-subtitle">{{ model.authMode === 'login' ? '登录后访问你的资料、会话和求职记录。' : '注册后即可开始构建自己的学习资料库。' }}</p>

          <div class="auth-switch">
            <button type="button" :class="{active: model.authMode === 'login'}" @click="model.authMode = 'login'">登录</button>
            <button type="button" :class="{active: model.authMode === 'register'}" @click="model.authMode = 'register'">注册</button>
          </div>

          <label class="form-field">
            <span>账号</span>
            <input v-model.trim="model.authForm.username" autocomplete="username" placeholder="请输入账号" required>
          </label>
          <label class="form-field">
            <span>密码</span>
            <input v-model="model.authForm.password" type="password" :autocomplete="model.authMode === 'login' ? 'current-password' : 'new-password'" placeholder="至少 6 位" required minlength="6">
          </label>
          <template v-if="model.authMode === 'register'">
            <label class="form-field">
              <span>昵称</span>
              <input v-model.trim="model.authForm.displayName" placeholder="例如：小知">
            </label>
            <label class="form-field">
              <span>学习方向</span>
              <input v-model.trim="model.authForm.department" placeholder="例如：Java 后端">
            </label>
          </template>

          <button class="btn primary login-submit" type="submit">
            {{ model.authMode === 'login' ? '登录知途 AI' : '创建成长空间' }}
            <AppIcon name="arrow" :size="18"/>
          </button>

          <div class="login-status"><span class="status-dot"></span>{{ model.statusMessage }}</div>
          <p class="privacy-note">你的资料与求职记录仅在登录账号下访问。</p>
        </form>
      </section>
    </div>
  </main>
</template>
