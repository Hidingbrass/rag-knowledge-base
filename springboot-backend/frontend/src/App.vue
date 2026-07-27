<script>
import appOptions from "./appOptions.js";
import AppHeader from "@/components/AppHeader.vue";
import AppIcon from "@/components/AppIcon.vue";
import LoginView from "@/views/LoginView.vue";
import SystemDrawer from "@/components/SystemDrawer.vue";
import ProfileDrawer from "@/components/ProfileDrawer.vue";

const routeByTab = {
  overview: "/",
  knowledge: "/library",
  chat: "/study",
  jobs: "/career"
};

export default {
  name: "ZhiTuApp",
  extends: appOptions,
  components: {AppHeader, AppIcon, LoginView, SystemDrawer, ProfileDrawer},
  provide() {
    return {workspace: this};
  },
  data() {
    return {
      statusDrawerOpen: false,
      profileDrawerOpen: false,
      mobileNavOpen: false,
      careerStage: "analyze",
      careerResultTab: "analysis"
    };
  },
  watch: {
    "$route.meta.tab": {
      immediate: true,
      handler(tab) {
        if (tab) {
          this.activeTab = tab;
        }
      }
    }
  },
  methods: {
    navigateTo(tab) {
      const path = routeByTab[tab] || "/";
      this.activeTab = tab;
      this.mobileNavOpen = false;
      if (this.$route.path !== path) {
        this.$router.push(path);
      }
    },
    openStudyWithPrompt(prompt) {
      this.chatForm.question = prompt;
      this.navigateTo("chat");
    }
  }
};
</script>

<template>
  <LoginView v-if="!isAuthenticated" :model="$root"/>

  <div v-else class="app-frame">
    <AppHeader :model="$root"/>

    <main class="app-main">
      <RouterView/>
    </main>

    <nav class="mobile-tabbar" aria-label="移动端主导航">
      <button :class="{active: activeTab === 'overview'}" @click="navigateTo('overview')">
        <AppIcon name="home" :size="20"/><span>主页</span>
      </button>
      <button :class="{active: activeTab === 'knowledge'}" @click="navigateTo('knowledge')">
        <AppIcon name="library" :size="20"/><span>资料</span>
      </button>
      <button :class="{active: activeTab === 'chat'}" @click="navigateTo('chat')">
        <AppIcon name="sparkles" :size="20"/><span>伴学</span>
      </button>
      <button :class="{active: activeTab === 'jobs'}" @click="navigateTo('jobs')">
        <AppIcon name="briefcase" :size="20"/><span>求职</span>
      </button>
    </nav>

    <button
      class="status-toast"
      :class="{
        running: chatStreaming || jobOperation.state === 'running',
        success: !chatStreaming && jobOperation.state === 'success',
        error: jobOperation.state === 'error' || chatStreamStage === 'failed'
      }"
      aria-live="polite"
      @click="statusDrawerOpen = true"
      title="查看运行状态"
    >
      <span v-if="chatStreaming || jobOperation.state === 'running'" class="mini-spinner"></span>
      <span v-else class="status-dot"></span>
      <span>{{ statusMessage }}</span>
      <AppIcon name="chevron" :size="15"/>
    </button>

    <SystemDrawer :model="$root" :open="statusDrawerOpen" @close="statusDrawerOpen = false"/>
    <ProfileDrawer :model="$root" :open="profileDrawerOpen" @close="profileDrawerOpen = false"/>
  </div>
</template>
