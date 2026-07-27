<script setup>
import {computed, inject} from "vue";
import AppIcon from "@/components/AppIcon.vue";
import EmptyState from "@/components/EmptyState.vue";

const app = inject("workspace");

const growthSteps = computed(() => [
  {label: "创建学习资料库", done: app.knowledgeBases.length > 0},
  {label: "上传并解析资料", done: app.stats.availableDocs > 0},
  {label: "开始一次 AI 伴学", done: app.sessions.length > 0},
  {label: "完成一次岗位分析", done: app.jobPages.tasks.totalElements > 0}
]);

const completedSteps = computed(() => growthSteps.value.filter(item => item.done).length);
const growthPercent = computed(() => completedSteps.value * 25);
const recentSessions = computed(() => app.sessions.slice(0, 4));
const recentKnowledgeBases = computed(() => app.knowledgeBases.slice(0, 3));

const nextSuggestion = computed(() => {
  if (!app.knowledgeBases.length) {
    return {title: "先创建第一份学习资料库", text: "按照技能方向整理八股文、课程笔记或项目文档。", action: "创建资料库", tab: "knowledge"};
  }
  if (!app.stats.availableDocs) {
    return {title: "上传一份最常复习的资料", text: "资料解析完成后，AI 才能基于原文陪你学习。", action: "上传资料", tab: "knowledge"};
  }
  if (!app.sessions.length) {
    return {title: "用一个问题启动学习", text: "从概念解释、知识对比或模拟面试开始。", action: "开始伴学", tab: "chat"};
  }
  return {title: "把知识转化为面试表达", text: "让 AI 根据资料生成一道面试题，并尝试口述回答。", action: "继续伴学", tab: "chat"};
});

const prompts = [
  "根据资料总结今天最值得复习的 5 个知识点",
  "从当前文档生成 3 道递进式面试题",
  "用通俗例子解释资料里最难理解的概念"
];
</script>

<template>
  <div class="page-stack overview-page">
    <section class="hero-panel">
      <div class="hero-content">
        <span class="hero-kicker"><AppIcon name="sparkles" :size="16"/>你的个人成长空间</span>
        <h1>你好，{{ app.currentUserLabel }}。<br><em>今天想向目标再靠近一点吗？</em></h1>
        <p>把学习资料变成可以随时提问的知识库，再把掌握的知识转化为求职竞争力。</p>
        <div class="hero-actions">
          <button class="btn primary" @click="app.navigateTo('chat')"><AppIcon name="chat" :size="18"/>开始 AI 伴学</button>
          <button class="btn soft" @click="app.navigateTo('knowledge')"><AppIcon name="upload" :size="18"/>上传学习资料</button>
        </div>
      </div>
      <div class="hero-visual" aria-hidden="true">
        <div class="path-card path-main">
          <span class="path-icon"><AppIcon name="target" :size="26"/></span>
          <div><small>本阶段成长进度</small><strong>{{ growthPercent }}%</strong></div>
          <div class="mini-progress"><i :style="{width: `${growthPercent}%`}"></i></div>
        </div>
        <div class="path-card floating-card card-learn"><AppIcon name="library"/><span>资料沉淀</span></div>
        <div class="path-card floating-card card-ai"><AppIcon name="sparkles"/><span>AI 伴学</span></div>
        <div class="path-card floating-card card-job"><AppIcon name="briefcase"/><span>求职输出</span></div>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card purple">
        <span><AppIcon name="library"/></span>
        <div><small>学习资料库</small><strong>{{ app.knowledgeBases.length }}</strong><p>按技能方向持续沉淀</p></div>
      </article>
      <article class="metric-card mint">
        <span><AppIcon name="document"/></span>
        <div><small>可学习资料</small><strong>{{ app.stats.availableDocs }}</strong><p>{{ app.stats.processingDocs }} 份正在解析</p></div>
      </article>
      <article class="metric-card peach">
        <span><AppIcon name="chat"/></span>
        <div><small>伴学会话</small><strong>{{ app.sessions.length }}</strong><p>保留连续学习上下文</p></div>
      </article>
      <article class="metric-card amber">
        <span><AppIcon name="briefcase"/></span>
        <div><small>岗位分析</small><strong>{{ app.jobPages.tasks.totalElements }}</strong><p>{{ app.jobPages.favorites.totalElements }} 个关注岗位</p></div>
      </article>
    </section>

    <section class="overview-grid">
      <div class="surface-card growth-card">
        <div class="section-heading">
          <div><span class="eyebrow">成长路线</span><h2>从资料到 offer 的四步闭环</h2></div>
          <span class="progress-label">{{ completedSteps }}/4 已完成</span>
        </div>
        <div class="growth-track"><i :style="{width: `${growthPercent}%`}"></i></div>
        <div class="growth-steps">
          <article v-for="(step, index) in growthSteps" :key="step.label" :class="{done: step.done}">
            <span>{{ step.done ? '✓' : index + 1 }}</span>
            <div><strong>{{ step.label }}</strong><small>{{ step.done ? '已经完成' : '等待解锁' }}</small></div>
          </article>
        </div>
      </div>

      <aside class="surface-card suggestion-card">
        <span class="suggestion-icon"><AppIcon name="sparkles" :size="24"/></span>
        <span class="eyebrow">下一步建议</span>
        <h2>{{ nextSuggestion.title }}</h2>
        <p>{{ nextSuggestion.text }}</p>
        <button class="text-button" @click="app.navigateTo(nextSuggestion.tab)">{{ nextSuggestion.action }}<AppIcon name="arrow" :size="17"/></button>
      </aside>
    </section>

    <section class="overview-grid lower-grid">
      <div class="surface-card">
        <div class="section-heading">
          <div><span class="eyebrow">学习空间</span><h2>最近资料库</h2></div>
          <button class="text-button" @click="app.navigateTo('knowledge')">查看全部<AppIcon name="arrow" :size="16"/></button>
        </div>
        <div v-if="recentKnowledgeBases.length" class="compact-list">
          <button v-for="kb in recentKnowledgeBases" :key="kb.id" @click="app.selectKnowledgeBase(kb.id); app.navigateTo('knowledge')">
            <span class="list-icon"><AppIcon name="library"/></span>
            <div><strong>{{ kb.name }}</strong><small>{{ kb.description || '尚未添加描述' }}</small></div>
            <span class="direction-tag">{{ kb.department || '综合学习' }}</span>
            <AppIcon name="chevron" :size="17"/>
          </button>
        </div>
        <EmptyState v-else icon="library" title="还没有学习资料库" description="创建一个资料库，开始积累自己的知识资产。">
          <button class="btn soft small" @click="app.navigateTo('knowledge')">立即创建</button>
        </EmptyState>
      </div>

      <div class="surface-card">
        <div class="section-heading">
          <div><span class="eyebrow">继续学习</span><h2>最近伴学会话</h2></div>
          <button class="text-button" @click="app.navigateTo('chat')">进入伴学<AppIcon name="arrow" :size="16"/></button>
        </div>
        <div v-if="recentSessions.length" class="session-preview-list">
          <button v-for="session in recentSessions" :key="session.id" @click="app.selectedSessionId = session.id; app.loadMessages(); app.navigateTo('chat')">
            <span><AppIcon name="chat" :size="18"/></span>
            <div><strong>{{ session.title }}</strong><small>{{ app.formatDate(session.updatedAt || session.createdAt) }}</small></div>
          </button>
        </div>
        <EmptyState v-else icon="chat" title="还没有伴学会话" description="从一份已解析资料开始向 AI 提问。"/>
      </div>
    </section>

    <section class="surface-card prompt-card">
      <div class="section-heading">
        <div><span class="eyebrow">灵感问题</span><h2>不知道从哪里开始？</h2></div>
      </div>
      <div class="prompt-grid">
        <button v-for="prompt in prompts" :key="prompt" @click="app.openStudyWithPrompt(prompt)">
          <AppIcon name="sparkles" :size="18"/><span>{{ prompt }}</span><AppIcon name="arrow" :size="17"/>
        </button>
      </div>
    </section>
  </div>
</template>
