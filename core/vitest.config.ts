import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["test/**/*.test.ts"],
    // Calendar maths uses local time, so the tests pin a zone.
    env: { TZ: "Asia/Kolkata" },
  },
});
