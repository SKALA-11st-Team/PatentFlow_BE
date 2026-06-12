import { expect, type Browser, type Page, request as playwrightRequest } from "@playwright/test";

export const BE_BASE_URL = process.env.E2E_BE_URL ?? "http://localhost:18080";

export const ADMIN = {
  email: process.env.E2E_ADMIN_EMAIL ?? "admin@syuuk.test",
  password: process.env.E2E_ADMIN_PASSWORD ?? "E2eAdmin!2026",
};

export const BUSINESS = {
  email: process.env.E2E_BUSINESS_EMAIL ?? "ict.manager@syuuk.test",
  password: process.env.E2E_BUSINESS_PASSWORD ?? "E2eBusiness!2026",
};

/** BE가 응답할 때까지 대기 (5xx/connection refused 동안 폴링). */
export async function waitForBackend(timeoutMs = 120_000): Promise<void> {
  const ctx = await playwrightRequest.newContext();
  const deadline = Date.now() + timeoutMs;
  let lastError = "no response";
  while (Date.now() < deadline) {
    try {
      const res = await ctx.get(`${BE_BASE_URL}/api/v1/notifications`, { timeout: 5_000 });
      // 401/403 포함 — HTTP 응답 자체가 오면 기동 완료로 본다.
      if (res.status() < 500) {
        await ctx.dispose();
        return;
      }
      lastError = `status ${res.status()}`;
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  await ctx.dispose();
  throw new Error(`BE(${BE_BASE_URL})가 기동되지 않았습니다: ${lastError}`);
}

/** UI 로그인. LoginPage의 역할 탭 + 이메일/비밀번호 폼을 사용한다. */
export async function loginViaUi(
  page: Page,
  role: "ADMIN" | "BUSINESS",
  credentials: { email: string; password: string },
): Promise<void> {
  await page.goto("/login");
  await page.getByRole("button", { name: role === "ADMIN" ? "관리자" : "사업부서", exact: true }).click();
  await page.locator('input[type="email"]').fill(credentials.email);
  await page.locator('input[type="password"]').fill(credentials.password);
  await page
    .getByRole("button", { name: role === "ADMIN" ? "관리자로 로그인" : "사업부서로 로그인" })
    .click();
  await expect(page).toHaveURL(
    role === "ADMIN" ? /\/admin\/dashboard/ : /\/business\/dashboard/,
    { timeout: 20_000 },
  );
  // 인증 부트스트랩 완료 신호: AppLayout(로그아웃 링크 포함)은 ProtectedRoute가 인증을 확정해야
  // 렌더된다. 이걸 기다리지 않고 곧바로 full-navigation(goto)하면 부트스트랩 중간에 끼어들어
  // /login으로 튕기는 레이스가 난다(로그인 직후 토큰 회전 창과 겹침).
  await expect(page.getByText("로그아웃")).toBeVisible({ timeout: 20_000 });
}

/**
 * 새 컨텍스트를 만들어 신선하게 로그인한 페이지를 돌려준다.
 *
 * storageState 재사용은 일부러 쓰지 않는다: BE의 리프레시 토큰이 1회용 회전(rotation)이라
 * 저장된 상태를 여러 컨텍스트가 공유하면 소모된 토큰 재사용 → 재사용 탐지 → 전체 세션 revoke로
 * suite 중간에 모든 요청이 401이 된다. 컨텍스트당 UI 로그인(~2초)이 안정성 비용으로 더 싸다.
 */
export async function loginPage(browser: Browser, role: "ADMIN" | "BUSINESS"): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await loginViaUi(page, role, role === "ADMIN" ? ADMIN : BUSINESS);
  return page;
}

/** API 로그인 → Bearer 토큰. (CSRF exempt 경로라 토큰만으로 GET 가능) */
async function apiLogin(): Promise<{ ctx: Awaited<ReturnType<typeof playwrightRequest.newContext>>; token: string }> {
  const ctx = await playwrightRequest.newContext();
  const res = await ctx.post(`${BE_BASE_URL}/api/v1/auth/login`, {
    data: { email: ADMIN.email, password: ADMIN.password },
  });
  if (!res.ok()) {
    throw new Error(`API login failed: ${res.status()}`);
  }
  const body = (await res.json()) as { data?: { accessToken?: string }; accessToken?: string };
  const token = body.data?.accessToken ?? body.accessToken ?? "";
  return { ctx, token: token.replace(/^Bearer\s+/i, "") };
}

/** 관리자 권한으로 BE API를 직접 GET 한다 (매 호출 신선한 로그인 — 회전 토큰과 무충돌). */
export async function apiGet<T>(path: string): Promise<T> {
  const { ctx, token } = await apiLogin();
  const res = await ctx.get(`${BE_BASE_URL}/api/v1${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    const body = await res.text();
    await ctx.dispose();
    throw new Error(`GET ${path} -> ${res.status()}: ${body.slice(0, 300)}`);
  }
  const json = (await res.json()) as T;
  await ctx.dispose();
  return json;
}
