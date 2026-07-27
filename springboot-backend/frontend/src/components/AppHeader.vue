<script setup>
import BrandMark from "./BrandMark.vue";
import AppIcon from "./AppIcon.vue";

defineProps({model: {type: Object, required: true}});

const navItems = [
  {tab: "overview", label: "成长主页", icon: "home"},
  {tab: "knowledge", label: "学习资料库", icon: "library"},
  {tab: "chat", label: "AI 伴学", icon: "sparkles"},
  {tab: "jobs", label: "求职工具箱", icon: "briefcase"}
];
</script>

<template>
  <header class="app-header">
    <div class="header-inner">
      <button class="brand-button" @click="model.navigateTo('overview')" aria-label="返回成长主页">
        <BrandMark/>
      </button>

      <nav class="desktop-nav" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.tab"
          :class="{active: model.activeTab === item.tab}"
          @click="model.navigateTo(item.tab)"
        >
          <AppIcon :name="item.icon" :size="18"/>
          <span>{{ item.label }}</span>
          <i v-if="item.tab === 'knowledge' && model.knowledgeBases.length">{{ model.knowledgeBases.length }}</i>
        </button>
      </nav>

      <div class="header-actions">
        <button class="icon-button status-button" @click="model.statusDrawerOpen = true" title="系统状态">
          <AppIcon name="settings" :size="19"/>
        </button>
        <button class="user-pill" type="button" title="编辑个人资料" @click="model.openProfileEditor">
          <span class="user-avatar">{{ model.authInitial }}</span>
          <span class="user-copy">
            <strong>{{ model.currentUserLabel }}</strong>
            <small>{{ model.identity.department || '个人空间' }}</small>
          </span>
        </button>
        <button class="icon-button logout-button" @click="model.logout" title="退出登录">
          <AppIcon name="logout" :size="19"/>
        </button>
      </div>
    </div>
  </header>
</template>
