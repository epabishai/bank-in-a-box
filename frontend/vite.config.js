import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.js",
    coverage: {
      provider: "v8",
      reporter: ["text", "lcov"],
      include: ["src/components/**/*.{js,jsx}", "src/api/**/*.{js,jsx}"],
      thresholds: { lines: 80, branches: 80, functions: 80, statements: 80 },
    },
  },
});
