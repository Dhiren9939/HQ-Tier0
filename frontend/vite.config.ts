import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [tailwindcss(), reactRouter()],
  resolve: {
    tsconfigPaths: true,
  },
  server: {
    // Backend (HQ/api) runs on 8081 in the dev profile. Proxying keeps
    // /api/* same-origin in dev so an unimplemented endpoint fails with a
    // clean 404 instead of an opaque CORS error.
    proxy: {
      "/api": { target: "http://localhost:8081", changeOrigin: true },
      // Spring Security's default OAuth2 login redirect endpoint.
      "/oauth2": { target: "http://localhost:8081", changeOrigin: true },
    },
  },
});
