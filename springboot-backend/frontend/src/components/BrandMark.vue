<script setup>
defineProps({
  compact: {type: Boolean, default: false},
  light: {type: Boolean, default: false}
});
</script>

<template>
  <div class="brand-lockup" :class="{compact, light}">
    <div class="brand-symbol" aria-hidden="true">
      <span>知</span>
      <i></i>
    </div>
    <div v-if="!compact" class="brand-copy">
      <strong>知途 AI</strong>
      <span>学习与求职成长助手</span>
    </div>
  </div>
</template>
