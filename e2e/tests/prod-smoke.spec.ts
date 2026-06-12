import { expect, test } from "@playwright/test";

/**
 * 시나리오 14 — 운영(patentflow.live) 읽기 전용 스모크. @prod
 *
 *   E2E_BASE_URL=https://patentflow.live \
 *   PROD_ADMIN_EMAIL=... PROD_ADMIN_PASSWORD=... npx playwright test --grep @prod
 *
 * 절대 금지: 변형 액션(레포트 생성, 메일 발송, 의견 제출, 최종 판단) — 데모 시드가 오염된다.
 */
test.describe("@prod 운영 스모크 (읽기 전용)", () => {
  test("로그인 화면이 렌더된다", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByRole("heading", { name: /SK AX 특허 관리/ })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: "관리자로 로그인" })).toBeVisible();
  });

  test("관리자 로그인 → 대시보드/목록 렌더 (조회만)", async ({ page }) => {
    const email = process.env.PROD_ADMIN_EMAIL;
    const password = process.env.PROD_ADMIN_PASSWORD;
    test.skip(!email || !password, "PROD_ADMIN_EMAIL/PASSWORD 미설정");

    await page.goto("/login");
    await page.getByRole("button", { name: "관리자", exact: true }).click();
    await page.locator('input[type="email"]').fill(email!);
    await page.locator('input[type="password"]').fill(password!);
    await page.getByRole("button", { name: "관리자로 로그인" }).click();
    await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 30_000 });

    await page.goto("/admin/patents");
    await expect(page.getByRole("table").first()).toBeVisible({ timeout: 30_000 });

    // 알림 패널 열기(조회) 후 로그아웃
    await page.getByRole("button", { name: /알림/ }).click();
    await expect(page.getByRole("dialog", { name: "알림" })).toBeVisible();
    await page.keyboard.press("Escape");
    await page.getByText("로그아웃").click();
    await expect(page).toHaveURL(/\/login/);
  });
});
