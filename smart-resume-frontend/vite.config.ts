import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In dev, /api calls are proxied to the Spring Boot backend, so no CORS setup is needed.
// The Origin header is dropped so the backend treats these as same-origin requests.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        configure: (proxy) => proxy.on("proxyReq", (req) => req.removeHeader("origin")),
      },
    },
  },
});
