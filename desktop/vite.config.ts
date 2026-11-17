import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

// Tauri serves the built files; `base: "./"` also lets the web build run from any folder.
export default defineConfig({
  base: "./",
  plugins: [react()],
  resolve: {
    alias: {
      "@core": fileURLToPath(new URL("../core/src/index.ts", import.meta.url)),
    },
  },
  server: { port: 1420, strictPort: true },
  clearScreen: false,
  build: { target: "es2022", outDir: "dist", emptyOutDir: true },
});
