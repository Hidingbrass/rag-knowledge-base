import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";
import {fileURLToPath, URL} from "node:url";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": "http://127.0.0.1:8081",
      "/health": "http://127.0.0.1:8081"
    }
  },
  build: {
    outDir: "../src/main/resources/static",
    emptyOutDir: false,
    rollupOptions: {
      output: {
        entryFileNames: "assets/zhitu-app.js",
        chunkFileNames: "assets/zhitu-[name].js",
        assetFileNames: assetInfo => assetInfo.names?.some(name => name.endsWith(".css"))
          ? "assets/zhitu-style.css"
          : "assets/zhitu-[name][extname]"
      }
    }
  },
  test: {
    environment: "jsdom",
    globals: true
  }
});
