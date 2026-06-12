import { expect, test } from "@playwright/test";
import { apiGet, loginPage } from "./helpers";

/**
 * 시나리오 11 — 연차료 납부 일정 조회 (FR-LEGAL-24)
 * BE fee-schedule API가 단일 출처(FEE-06)인 일정 카드가 상세 페이지에 렌더되는지 확인한다.
 */
test("특허 상세에 연차료 납부 일정이 표시된다", async ({ browser }) => {
  const json = await apiGet<{ data?: Array<{ patentId: string; title: string }> } | Array<{ patentId: string; title: string }>>(
    "/patents/review-targets?reviewWorkflowStatus=REVIEW_QUARTER_STARTED",
  );
  const targets = Array.isArray(json) ? json : (json.data ?? []);
  test.skip(targets.length === 0, "검토 대상 없음 — journey 이전 상태 필요");
  const target = targets[0];

  const page = await loginPage(browser, "ADMIN");
  await page.goto(`/admin/patents/${target.patentId}`);

  await expect(page.getByRole("heading", { name: target.title })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText("연차료 납부 일정")).toBeVisible();
  await expect(page.getByText("연차료 납부 예정일")).toBeVisible();
  await page.context().close();
});
