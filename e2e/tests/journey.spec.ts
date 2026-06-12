import { expect, test, type Page } from "@playwright/test";
import { apiGet, loginPage } from "./helpers";

/**
 * 시나리오 3~9 — 워크플로 5단계 전체를 한 특허로 통과하는 journey.
 *
 * NOT_IN_REVIEW → (분기 활성화: 기동 시 자동) REVIEW_QUARTER_STARTED
 *   → [AI 레포트 생성(fake-agent)] MAIL_READY
 *   → [메일 발송(simulate-delivery)] WAITING_BUSINESS_RESPONSE
 *   → [사업부 체크리스트 제출] BUSINESS_RESPONSE_RECEIVED
 *   → [최종 판단(납부 완료)] NOT_IN_REVIEW
 *
 * 상태가 누적되므로 serial 모드 + 단일 특허를 공유한다.
 * 검증 기준 문서: patentFlow_docs/be_data_flow.md §5
 */
test.describe.configure({ mode: "serial" });

interface PatentListItem {
  patentId: string;
  title: string;
  managementNumber: string;
  departmentId: string | null;
  coApplicants: string | null;
  reviewWorkflowStatus: string;
}

let patent: PatentListItem;

async function fetchReviewTargets(status: string): Promise<PatentListItem[]> {
  const json = await apiGet<{ data?: PatentListItem[] } | PatentListItem[]>(
    `/patents/review-targets?reviewWorkflowStatus=${status}`,
  );
  return Array.isArray(json) ? json : (json.data ?? []);
}

async function openAdminDetail(page: Page): Promise<void> {
  await page.goto(`/admin/patents/${patent.patentId}`);
  await expect(page.getByRole("heading", { name: patent.title })).toBeVisible({ timeout: 20_000 });
}

test("③ 분기 활성화: 검토 대상이 REVIEW_QUARTER_STARTED로 생성된다 (FR-LEGAL-22)", async ({ browser }) => {
  const targets = await fetchReviewTargets("REVIEW_QUARTER_STARTED");
  expect(targets.length, "기동 시 QuarterActivationScheduler가 검토 대상을 만들어야 한다").toBeGreaterThan(0);

  // journey용 특허 선정: ICT 부서 배정 + 단독 출원(공동출원 합의 게이트 회피)
  // coApplicants는 null이 아니라 "없음" 문자열이 기본값(PatentReviewService.newPatentFromRequest)
  const isSolo = (t: PatentListItem) => !t.coApplicants || t.coApplicants === "없음";
  const candidate =
    targets.find((t) => t.departmentId === "DEPT-ICT" && isSolo(t)) ?? targets.find(isSolo);
  expect(candidate, "공동출원이 아닌 검토 대상이 1건 이상 필요").toBeTruthy();
  patent = candidate!;

  // UI 확인: 상세 페이지가 REVIEW_QUARTER_STARTED 상태(레포트 생성 필요)를 표시한다.
  // (review-targets 목록 화면은 기본 분기 필터에 따라 해당 특허가 안 보일 수 있어 상세로 검증)
  const page = await loginPage(browser, "ADMIN");
  await page.goto(`/admin/patents/${patent.patentId}`);
  await expect(page.getByRole("heading", { name: patent.title })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText("AI 레포트 생성 필요")).toBeVisible();
  await page.context().close();
});

test("③-1 부서 미배정이면 ICT 사업부로 배정한다 (FR-LEGAL-12)", async ({ browser }) => {
  test.skip(patent.departmentId === "DEPT-ICT", "이미 ICT 배정됨");

  const page = await loginPage(browser, "ADMIN");
  await page.goto("/admin/review-targets");
  const row = page.getByRole("row").filter({ hasText: patent.managementNumber }).first();
  await row.locator('input[type="checkbox"]').check();
  await page.getByLabel("배정할 사업부").selectOption("DEPT-ICT");
  await page.getByRole("button", { name: "선택 항목 배정" }).click();
  await expect(row.getByText(/ICT/)).toBeVisible({ timeout: 15_000 });
  await page.context().close();
});

test("④ AI 레포트 요청 → 잡 폴링 → MAIL_READY (FR-LEGAL-05~08, 18)", async ({ browser }) => {
  test.setTimeout(180_000);
  const page = await loginPage(browser, "ADMIN");
  await openAdminDetail(page);

  await page.getByRole("button", { name: "AI 레포트 생성", exact: true }).click();
  // fake-agent 지연 동안 진행 메시지(생성 중/단계 라벨)가 보였다가 완료된다.
  // 완료 시 성공 메시지와 다음 단계 안내가 둘 다 뜨므로 first()로 매칭한다.
  await expect(page.getByText(/AI 레포트 생성이 완료|사업부 메일 발송 필요/).first()).toBeVisible({
    timeout: 120_000,
  });

  // fixture 내용이 리포트로 렌더되는지 — 4축 점수와 권고
  await expect(page.getByText("사업부 메일 발송 필요")).toBeVisible();
  await expect(page.getByText("권리성").first()).toBeVisible();
  await expect(page.getByText("유지 권고").first()).toBeVisible();
  await page.context().close();
});

test("⑤ 리포트 수정: 오버라이드 저장 + 원본 보존 (FR-LEGAL-09)", async ({ browser }) => {
  const page = await loginPage(browser, "ADMIN");
  await openAdminDetail(page);

  await page.getByRole("button", { name: "레포트 수정" }).click();
  await expect(page.getByRole("heading", { name: "AI 레포트 수정" })).toBeVisible();

  // 수정 가능한 첫 텍스트 영역에 법무 코멘트를 덧붙인다.
  const editor = page.locator(".ai-report-edit-modal textarea").first();
  await editor.fill((await editor.inputValue()) + "\n\n(법무 수정: e2e 검증 코멘트)");
  await page.getByRole("button", { name: /수정 저장|확인하고 저장/ }).click();

  // 수정본 표시 + 원본 보기 토글 존재 = 원본 보존의 UI 증거
  await expect(page.getByText(/법무 수정|수정본 보기|AI 원본 보기/).first()).toBeVisible({ timeout: 15_000 });
  await page.context().close();
});

test("⑥ 메일 발송(simulate) → WAITING_BUSINESS_RESPONSE (FR-LEGAL-13, 14, 23)", async ({ browser }) => {
  const page = await loginPage(browser, "ADMIN");
  await openAdminDetail(page);

  await page.getByRole("button", { name: "사업부 메일 발송" }).click();
  await expect(page.getByRole("heading", { name: "사업부 검토 요청 메일 미리보기" })).toBeVisible();
  await page.getByRole("button", { name: "최종 발송 확인" }).click();
  await expect(page.getByRole("heading", { name: "선택한 메일을 보낼까요?" })).toBeVisible();
  await page.getByRole("button", { name: "보내기" }).click();

  // simulate-delivery로 SENT 기록 → 상태 전이 (상태 배지/제목/버튼 여러 곳에 표시됨)
  await expect(page.getByText(/사업부 응답 대기|메일 발송 내역 보기/).first()).toBeVisible({ timeout: 30_000 });
  await page.context().close();
});

test("⑦ 사업부: 체크리스트 제출 → BUSINESS_RESPONSE_RECEIVED (FR-BUS-01, 04, 05)", async ({ browser }) => {
  test.setTimeout(150_000);
  const page = await loginPage(browser, "BUSINESS");
  await page.goto(`/business/patents/${patent.patentId}`);
  await expect(page.getByRole("heading", { name: patent.title })).toBeVisible({ timeout: 20_000 });

  await page.getByRole("button", { name: "의견 작성" }).click();
  await expect(page.getByRole("heading", { name: "평가 체크리스트" })).toBeVisible();

  // 모든 체크리스트 항목에 점수 선택 (각 항목의 첫 번째 라디오)
  // 라디오 input은 CSS로 숨겨져(라벨 카드 UI) actionability를 못 만족하므로 감싼 label을 클릭한다.
  const items = page.locator("fieldset.checklist-item");
  const count = await items.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i += 1) {
    await items.nth(i).locator("label").first().click();
  }

  const modal = page.getByRole("dialog", { name: "사업부 평가 체크리스트" });
  // 정성 점수 + 의견(유지) + 판단 근거
  await modal.locator('input[type="number"]').fill("2");
  await modal.locator("select").selectOption("MAINTAIN");
  await modal.getByLabel("판단 근거").fill("핵심 제품 라인에서 사용 중인 기술 — e2e 검증 의견");
  await page.getByRole("button", { name: "관리자에게 전달" }).click();

  // 제출 완료 — 모달이 닫히고 의견이 배지/요약으로 표시된다.
  await expect(page.getByRole("heading", { name: "평가 체크리스트" })).toBeHidden({ timeout: 30_000 });
  await expect(page.getByText(/이미 제출한 의견|사업부 의견/).first()).toBeVisible();
  await page.context().close();
});

test("⑧ 최종 판단(납부 완료) → NOT_IN_REVIEW (FR-LEGAL-10, 19, 20)", async ({ browser }) => {
  const page = await loginPage(browser, "ADMIN");
  await openAdminDetail(page);

  // 사업부 의견이 MAINTAIN이므로 버튼 라벨은 "납부 완료"
  await expect(page.getByText("처리 결과 입력 필요")).toBeVisible({ timeout: 20_000 });
  await page.getByRole("button", { name: "납부 완료", exact: true }).click();
  await expect(page.getByRole("heading", { name: "납부 완료 확인" })).toBeVisible();
  await page.getByLabel("처리 사유").fill("AI 유지 권고 + 사업부 유지 의견 일치 — e2e 최종 판단");
  await page.locator(".modal-actions").getByRole("button", { name: "납부 완료" }).click();

  // 결정 박스: 유지 처리 배지 + 수정 버튼 (FR-LEGAL-20 진입점)
  await expect(page.getByText("유지 처리").first()).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole("button", { name: "최종 판단 수정" })).toBeVisible();
  await page.context().close();
});

test("⑨ 사이클 종료 후 평가·판단 이력이 남는다 (FR-LEGAL-11)", async ({ browser }) => {
  const page = await loginPage(browser, "ADMIN");
  await openAdminDetail(page);

  const history = page.locator("section", { hasText: "평가 및 판단 이력" }).first();
  await expect(history).toBeVisible();
  // 사이클 전 단계가 시간순 타임라인(article)으로 남는다: 최종 판단·사업부 의견·AI 레포트 생성.
  // 항목 제목은 <article><strong> — 섹션 설명 문구(p)와 구분하기 위해 article 내 strong으로 좁힌다.
  await expect(history.locator("article strong", { hasText: "최종 판단 기록" })).toBeVisible();
  await expect(history.locator("article strong", { hasText: "사업부 의견 제출" })).toBeVisible();
  await expect(history.locator("article strong", { hasText: "AI 평가 레포트 생성" })).toBeVisible();
  await expect(history.getByText(/MAINTAINED/)).toBeVisible();

  // DB 가시 효과 재확인: 이 특허는 더 이상 REVIEW_QUARTER_STARTED 목록에 없다.
  const stillStarted = await fetchReviewTargets("REVIEW_QUARTER_STARTED");
  expect(stillStarted.find((t) => t.patentId === patent.patentId)).toBeFalsy();
  await page.context().close();
});
