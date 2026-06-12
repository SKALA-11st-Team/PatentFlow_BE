import { expect, test } from "@playwright/test";
import { ADMIN, BUSINESS, loginPage, loginViaUi } from "./helpers";

/**
 * 시나리오 1·2 — 인증과 역할 가드 (FR-COM-01, UI-COM-01)
 */
test.describe("인증", () => {
  test("관리자 로그인/로그아웃", async ({ page }) => {
    await loginViaUi(page, "ADMIN", ADMIN);
    await expect(page).toHaveURL(/\/admin\/dashboard/);

    await page.getByText("로그아웃").click();
    await expect(page).toHaveURL(/\/login/);
  });

  test("잘못된 비밀번호는 오류 메시지를 보여준다", async ({ page }) => {
    await page.goto("/login");
    await page.getByRole("button", { name: "관리자", exact: true }).click();
    await page.locator('input[type="email"]').fill(ADMIN.email);
    await page.locator('input[type="password"]').fill("wrong-password-!!");
    await page.getByRole("button", { name: "관리자로 로그인" }).click();

    await expect(page.locator(".form-error")).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test("사업부 로그인은 사업부 대시보드로 이동한다", async ({ page }) => {
    await loginViaUi(page, "BUSINESS", BUSINESS);
    await expect(page).toHaveURL(/\/business\/dashboard/);
  });

  test("사업부 사용자는 관리자 라우트에 접근할 수 없다", async ({ browser }) => {
    const page = await loginPage(browser, "BUSINESS");
    await page.goto("/admin/dashboard");
    // ProtectedRoute(adminOnly)가 사업부 사용자를 자신의 화면으로 돌려보낸다.
    await expect(page).not.toHaveURL(/\/admin\/dashboard/);
    await expect(page).toHaveURL(/\/business|\/login/);
    await page.context().close();
  });
});
