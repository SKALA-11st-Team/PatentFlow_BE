import { defineConfig, devices } from "@playwright/test";

/**
 * PatentFlow E2E.
 *
 * 기본(로컬): docker-compose.e2e.yml 스택(BE+Postgres+fake-agent)을 먼저 띄운 뒤 실행한다.
 *   npm run stack:up && npx playwright test
 * FE dev 서버는 webServer 설정이 자동으로 띄운다 (VITE_USE_MOCK_API=false, 실제 BE 연결).
 *
 * 운영 스모크(읽기 전용): E2E_BASE_URL=https://patentflow.live npx playwright test --grep @prod
 *   - E2E_BASE_URL이 설정되면 FE dev 서버를 띄우지 않는다.
 *   - 변형 시나리오(@prod 아님)는 운영에 절대 돌리지 않는다.
 */
// 로컬 개발 서버(FE 5173 / BE 8080)와 충돌하지 않도록 e2e는 FE 5174 / BE 18080을 쓴다.
const isProdTarget = Boolean(process.env.E2E_BASE_URL);
const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:5174";

export default defineConfig({
  testDir: "./tests",
  outputDir: "./test-results",
  // FE의 .env.local(mock=true)을 우회하는 .env.test.local을 생성/정리한다.
  globalSetup: "./global-setup.ts",
  globalTeardown: "./global-teardown.ts",
  // 워크플로 상태가 누적되는 시나리오가 많아 단일 워커로 결정적으로 실행한다.
  workers: 1,
  fullyParallel: false,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    locale: "ko-KR",
    timezoneId: "Asia/Seoul",
  },
  projects: isProdTarget
    ? [
        {
          name: "prod-smoke",
          grep: /@prod/,
          use: { ...devices["Desktop Chrome"] },
        },
      ]
    : [
        {
          name: "setup",
          testMatch: /.*\.setup\.ts/,
        },
        {
          name: "chromium",
          grepInvert: /@prod|@real-agent/,
          dependencies: ["setup"],
          use: { ...devices["Desktop Chrome"] },
        },
        // 실제 Agent 스모크는 E2E_REAL_AGENT=1일 때만 프로젝트에 포함된다.
        // (기본 실행에 포함되면 fake-agent 스택에서 22분 타임아웃만 낭비한다)
        ...(process.env.E2E_REAL_AGENT
          ? [
              {
                name: "real-agent",
                grep: /@real-agent/,
                dependencies: ["setup"],
                timeout: 25 * 60_000,
                use: { ...devices["Desktop Chrome"] },
              },
            ]
          : []),
      ],
  webServer: isProdTarget
    ? undefined
    : {
        // --mode test로 띄워 global-setup이 만든 .env.test.local(mock off + base)이 적용되게 한다.
        command: "npm run dev -- --mode test --port 5174 --strictPort",
        cwd: "../PatentFlow_FE",
        url: "http://localhost:5174",
        reuseExistingServer: true,
        timeout: 120_000,
        env: {
          // 단일 오리진: FE dev 서버가 /api를 BE(18080)로 프록시한다.
          // 브라우저가 FE·API를 같은 오리진으로 보므로 쿠키(XSRF 포함)가 일관되게 동작한다.
          VITE_DEV_PROXY_TARGET: "http://localhost:18080",
        },
      },
});
