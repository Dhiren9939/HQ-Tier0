import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [tailwindcss(), reactRouter()],
  resolve: {
    tsconfigPaths: true,
  },
  server: {
    // The backend runs on 8080. Proxying keeps /api/* same-origin in dev, so an
    // unimplemented endpoint fails with a clean 404 instead of an opaque CORS error.
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
});
