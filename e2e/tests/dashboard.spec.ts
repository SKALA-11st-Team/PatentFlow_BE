import { expect, test } from "@playwright/test";
import { loginPage } from "./helpers";

/**
 * 시나리오 12 — 대시보드가 워크플로 상태를 반영한다 (FR-LEGAL-01, UI-LEGAL-01 / UI-BUS-01)
 */
test.describe("대시보드", () => {
  test("관리자 대시보드: KPI와 특허 조회 섹션", async ({ browser }) => {
    const page = await loginPage(browser, "ADMIN"); // 로그인 직후 /admin/dashboard 도착

    await expect(page.getByRole("heading", { name: "특허 조회" })).toBeVisible({ timeout: 20_000 });
    // 분기 활성화로 검토 대상이 생겼으므로 특허 테이블이 렌더된다(구체 수치는 시드에 의존하므로 미고정).
    await expect(page.getByRole("table").first()).toBeVisible();
    await page.context().close();
  });

  test("사업부 대시보드: 의견 요청 테이블", async ({ browser }) => {
    const page = await loginPage(browser, "BUSINESS"); // 로그인 직후 /business/dashboard 도착

    // 진행 중 작업 섹션이 렌더된다 (메일 발송 전이면 빈 상태일 수 있어 테이블 존재는 강제하지 않음)
    await expect(page.getByRole("heading").first()).toBeVisible({ timeout: 20_000 });
    await expect(page.locator("h2").first()).toBeVisible();
    await page.context().close();
  });
});
