<script setup>
import AppIcon from "./AppIcon.vue";

defineProps({
  icon: {type: String, default: "sparkles"},
  title: {type: String, required: true},
  description: {type: String, default: ""}
});
</script>

<template>
  <div class="empty-state">
    <div class="empty-icon"><AppIcon :name="icon" :size="25"/></div>
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <slot/>
  </div>
</template>
