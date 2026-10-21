import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  build: {
    // 本机原地发布时保留旧哈希分包，已打开的页面仍能继续导航。
    // 清理历史产物应在旧会话退出后的发布维护阶段单独进行。
    emptyOutDir: false,
  },
  server: {
    port: 5173,
    proxy: {
      // 开发环境统一从 /api 转发，避免浏览器跨域并保持生产接口地址可配置。
      "/api": {
        target: process.env.VITE_BACKEND_URL || "http://localhost:8080",
        changeOrigin: true,
        // 同一/api代理同时转发HTTP和一次性票据认证的WebSocket握手。
        ws: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
});
