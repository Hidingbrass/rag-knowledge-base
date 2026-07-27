<script setup>
import {computed} from "vue";
import AppIcon from "./AppIcon.vue";

const props = defineProps({sourcesJson: {type: String, default: ""}});

const sources = computed(() => {
  try {
    const value = JSON.parse(props.sourcesJson || "[]");
    return Array.isArray(value) ? value.slice(0, 5) : [];
  } catch (error) {
    return [];
  }
});

function sourceName(source) {
  return source.filename || source.document_id || source.documentId || "引用资料";
}

function sourceScore(source) {
  const value = source.rerank_score ?? source.fusion_score ?? source.vector_score ?? source.sparse_score;
  return typeof value === "number" ? value.toFixed(3) : value;
}
</script>

<template>
  <div v-if="sources.length" class="citation-list">
    <div class="citation-title"><AppIcon name="link" :size="15"/>回答依据</div>
    <div class="citation-items">
      <article v-for="(source, index) in sources" :key="`${sourceName(source)}-${index}`">
        <span>{{ index + 1 }}</span>
        <div><strong>{{ sourceName(source) }}</strong><small v-if="sourceScore(source)">相关度 {{ sourceScore(source) }}</small></div>
      </article>
    </div>
  </div>
</template>
