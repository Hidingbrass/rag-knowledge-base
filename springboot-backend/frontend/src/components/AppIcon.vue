<script setup>
import {computed} from "vue";

const props = defineProps({
  name: {type: String, required: true},
  size: {type: Number, default: 20},
  strokeWidth: {type: Number, default: 1.8}
});

const paths = {
  home: ["M3 10.5 12 3l9 7.5", "M5 9.5V21h14V9.5", "M9 21v-7h6v7"],
  library: ["M4 19.5A2.5 2.5 0 0 1 6.5 17H20", "M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z"],
  sparkles: ["m12 3-1.1 3.1L8 7.2l2.9 1.1L12 11l1.1-2.7L16 7.2l-2.9-1.1L12 3Z", "m5 12-.8 2.2L2 15l2.2.8L5 18l.8-2.2L8 15l-2.2-.8L5 12Z", "m18 13-1 2.8-2.8 1 2.8 1L18 21l1-3.2 3-1-3-1L18 13Z"],
  briefcase: ["M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2", "M4 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Z", "M2 12h20", "M10 12v2h4v-2"],
  upload: ["M12 16V4", "m7 9 5-5 5 5", "M4 20h16"],
  plus: ["M12 5v14", "M5 12h14"],
  arrow: ["M5 12h14", "m13 6 6 6-6 6"],
  chat: ["M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z"],
  document: ["M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z", "M14 2v6h6", "M8 13h8", "M8 17h6"],
  target: ["M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z", "M12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12Z", "M12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z"],
  check: ["m5 12 4 4L19 6"],
  clock: ["M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z", "M12 6v6l4 2"],
  user: ["M20 21a8 8 0 0 0-16 0", "M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z"],
  logout: ["M10 17l5-5-5-5", "M15 12H3", "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"],
  menu: ["M4 7h16", "M4 12h16", "M4 17h16"],
  close: ["m6 6 12 12", "m18 6-12 12"],
  settings: ["M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z", "M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-1.42 2.46-.08-.02a1.7 1.7 0 0 0-1.8.36 1.7 1.7 0 0 0-.82 1.72V22h-2.84v-.08a1.7 1.7 0 0 0-1.12-1.58 1.7 1.7 0 0 0-1.9.34l-.06.06-2.46-1.42.02-.08a1.7 1.7 0 0 0-.36-1.8A1.7 1.7 0 0 0 5 16.62H5V13.8h.08a1.7 1.7 0 0 0 1.58-1.12 1.7 1.7 0 0 0-.34-1.9l-.06-.06 1.42-2.46.08.02a1.7 1.7 0 0 0 1.8-.36 1.7 1.7 0 0 0 .82-1.72V6h2.84v.08a1.7 1.7 0 0 0 1.12 1.58 1.7 1.7 0 0 0 1.9-.34l.06-.06 2.46 1.42-.02.08a1.7 1.7 0 0 0 .36 1.8 1.7 1.7 0 0 0 1.72.82H21v2.84h-.08A1.7 1.7 0 0 0 19.4 15Z"],
  refresh: ["M20 7h-5V2", "M20 7a9 9 0 1 0 1 7"],
  edit: ["M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"],
  trash: ["M3 6h18", "M8 6V4h8v2", "M19 6l-1 16H6L5 6", "M10 11v6", "M14 11v6"],
  download: ["M12 3v12", "m7 10 5 5 5-5", "M5 21h14"],
  chevron: ["m9 18 6-6-6-6"],
  link: ["M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"]
};

const iconPaths = computed(() => paths[props.name] || paths.sparkles);
</script>

<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="path in iconPaths" :key="path" :d="path"/>
  </svg>
</template>
