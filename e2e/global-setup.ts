import { writeFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Vite는 `.env.local`을 셸 env보다 우선시킨다. PatentFlow_FE/.env.local에는
 * VITE_USE_MOCK_API=true가 박혀 있어 e2e가 mock 모드로 떠버린다(실제 BE 미접속).
 *
 * `.env.test.local`은 `.env.local`보다 우선(Vite 우선순위: .env.[mode].local 최상위)하므로,
 * webServer를 `--mode test`로 띄우고 여기서 그 파일을 써서 mock을 끄고 base URL을 고정한다.
 * 단일 오리진: base가 자기 자신(5174)이라 fetch가 dev 프록시를 거쳐 BE(18080)로 간다.
 *
 * E2E_BASE_URL(운영 스모크)에서는 webServer를 띄우지 않으므로 파일을 만들지 않는다.
 */
export default function globalSetup(): void {
  if (process.env.E2E_BASE_URL) {
    return;
  }
  const target = resolve(__dirname, "../PatentFlow_FE/.env.test.local");
  writeFileSync(
    target,
    ["# e2e 자동 생성 (global-setup.ts) — global-teardown이 삭제한다.", "VITE_USE_MOCK_API=false", "VITE_API_BASE_URL=http://localhost:5174", ""].join("\n"),
    "utf-8",
  );
}
