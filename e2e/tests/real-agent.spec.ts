import { expect, test } from "@playwright/test";
import { apiGet, loginPage } from "./helpers";

/**
 * 시나리오 13 — 실제 Agent 단일 특허 스모크. @real-agent
 * LLM 비용 발생 + 최대 20분 소요. 발표 전 1~2회만 실행한다.
 *
 *   OPENAI_API_KEY=... docker compose -f docker-compose.e2e.yml -f docker-compose.real-agent.yml up --build -d
 *   npx playwright test --project=real-agent
 */
test("@real-agent 실제 에이전트로 AI 레포트 1건 생성", async ({ browser }) => {
  test.setTimeout(25 * 60_000);

  const json = await apiGet<{ data?: Array<{ patentId: string; title: string; country: string }> } | Array<{ patentId: string; title: string; country: string }>>(
    "/patents/review-targets?reviewWorkflowStatus=REVIEW_QUARTER_STARTED",
  );
  const targets = Array.isArray(json) ? json : (json.data ?? []);
  // KR 특허가 KIPRIS 경로 커버리지가 가장 넓다.
  const target = targets.find((t) => t.country === "KR") ?? targets[0];
  expect(target, "REVIEW_QUARTER_STARTED 특허 필요").toBeTruthy();

  const page = await loginPage(browser, "ADMIN");
  await page.goto(`/admin/patents/${target!.patentId}`);
  await expect(page.getByRole("heading", { name: target!.title })).toBeVisible({ timeout: 20_000 });

  await page.getByRole("button", { name: "AI 레포트 생성", exact: true }).click();
  await expect(page.getByText(/AI 레포트 생성이 완료|사업부 메일 발송 필요|생성에 실패/)).toBeVisible({
    timeout: 22 * 60_000,
  });
  // 실패 메시지면 테스트 실패로 처리
  await expect(page.getByText(/생성에 실패/)).toBeHidden();
  await expect(page.getByText("권리성").first()).toBeVisible();
  await page.context().close();
});
