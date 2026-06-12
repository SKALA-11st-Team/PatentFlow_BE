import { rmSync } from "node:fs";
import { resolve } from "node:path";

/** global-setup이 만든 e2e 전용 .env.test.local을 정리한다. */
export default function globalTeardown(): void {
  if (process.env.E2E_BASE_URL) {
    return;
  }
  rmSync(resolve(__dirname, "../PatentFlow_FE/.env.test.local"), { force: true });
}
