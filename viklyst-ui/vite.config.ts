import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [tailwindcss(), react()],
  base: "/viklyst/",
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
