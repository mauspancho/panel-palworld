import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";

declare const process: {
  cwd: () => string;
};

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiTarget = env.VITE_API_PROXY_TARGET || "http://localhost:8080";

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        "/api": { target: apiTarget, changeOrigin: true, secure: false },
        "/login": { target: apiTarget, changeOrigin: true, secure: false },
        "/logout": { target: apiTarget, changeOrigin: true, secure: false }
      }
    }
  };
});
