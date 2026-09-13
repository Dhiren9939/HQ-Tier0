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
    // clean 404 instead of an opaque CORS error, and so auth cookies set by
    // the backend are usable from this origin without needing CORS at all.
    // The OAuth2 endpoints (authorization + redirect) live under /api/public/**
    // (see SecurityConfig), so this one rule covers the whole login flow -
    // Google's own redirect back after consent goes straight to the backend
    // port, never through this proxy.
    proxy: {
      "/api": { target: "http://localhost:8081", changeOrigin: true },
    },
  },
});
