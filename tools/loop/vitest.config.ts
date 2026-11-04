import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    // worker-codex.test.ts는 실제 bash 워커를 띄우므로 여유를 둔다.
    testTimeout: 30_000,
  },
});
