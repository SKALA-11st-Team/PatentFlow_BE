import { expect, test } from "@playwright/test";
import { loginPage } from "./helpers";

/**
 * 시나리오 10 — 알림 패널 (FR-COM-02, UI-COM-03)
 * journey가 먼저 실행되며 메일 발송/의견 수신 알림을 만들어 둔다 (파일명 정렬상 journey가 선행).
 */
test.describe("알림", () => {
  test("관리자: 알림 패널 열기와 그룹 표시", async ({ browser }) => {
    const page = await loginPage(browser, "ADMIN"); // 로그인 직후 대시보드 도착

    await page.getByRole("button", { name: /알림/ }).click();
    const panel = page.getByRole("dialog", { name: "알림" });
    await expect(panel).toBeVisible();
    // journey가 만든 알림(예: 사업부 의견 수신)이 오늘 그룹에 보인다.
    await expect(panel.getByText("오늘")).toBeVisible();
    await page.context().close();
  });

  test("사업부: 검토 요청 도착 알림이 보인다", async ({ browser }) => {
    const page = await loginPage(browser, "BUSINESS"); // 로그인 직후 대시보드 도착

    await page.getByRole("button", { name: /알림/ }).click();
    const panel = page.getByRole("dialog", { name: "알림" });
    await expect(panel).toBeVisible();
    await expect(panel.getByText(/검토 요청|검토 분기/).first()).toBeVisible();
    await page.context().close();
  });
});
