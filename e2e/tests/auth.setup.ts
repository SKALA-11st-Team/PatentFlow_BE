import { test as setup } from "@playwright/test";
import { waitForBackend } from "./helpers";

/**
 * suite 전제 확인. 로그인 상태 저장(storageState)은 쓰지 않는다 —
 * 회전형 리프레시 토큰과 충돌하므로 각 spec이 helpers.loginPage()로 신선하게 로그인한다.
 */
setup("backend가 기동되어 있다", async () => {
  setup.setTimeout(180_000);
  await waitForBackend();
});
