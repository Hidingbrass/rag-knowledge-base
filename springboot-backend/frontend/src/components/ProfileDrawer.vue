<script setup>
import AppIcon from "./AppIcon.vue";

defineProps({
  model: {type: Object, required: true},
  open: {type: Boolean, default: false}
});

defineEmits(["close"]);
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="open" class="drawer-layer" @click.self="$emit('close')">
        <aside class="system-drawer profile-drawer" aria-label="个人资料">
          <div class="drawer-header profile-drawer-header">
            <div>
              <span class="eyebrow">个人中心</span>
              <h2>编辑个人资料</h2>
            </div>
            <button class="icon-button" type="button" aria-label="关闭个人资料" @click="$emit('close')">
              <AppIcon name="close"/>
            </button>
          </div>

          <form class="drawer-body profile-form" @submit.prevent="model.updateProfile">
            <section class="profile-hero">
              <span class="profile-avatar">{{ model.authInitial }}</span>
              <div>
                <strong>{{ model.currentUserLabel }}</strong>
                <p>完善昵称和学习方向，让成长空间更符合你的目标。</p>
              </div>
            </section>

            <label class="form-field">
              <span>登录账号</span>
              <input v-model="model.profileForm.username" readonly aria-readonly="true">
              <small>账号用于数据归属，暂不支持修改。</small>
            </label>
            <label class="form-field">
              <span>昵称</span>
              <input v-model.trim="model.profileForm.displayName" maxlength="100" placeholder="请输入昵称">
            </label>
            <label class="form-field">
              <span>学习方向</span>
              <input v-model.trim="model.profileForm.department" maxlength="100" placeholder="例如：Java 后端、AI 应用开发">
            </label>

            <div class="profile-actions">
              <button class="btn secondary" type="button" @click="$emit('close')">取消</button>
              <button class="btn primary" type="submit"><AppIcon name="check" :size="17"/>保存资料</button>
            </div>
          </form>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>
