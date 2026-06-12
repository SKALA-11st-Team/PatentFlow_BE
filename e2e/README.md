# PatentFlow E2E (Playwright)

3개 저장소(BE/FE/Agent)를 가로지르는 E2E 테스트. **로컬 풀스택**(BE+Postgres+가짜 Agent)에서 변형 시나리오를 돌리고, 운영(patentflow.live)에는 읽기 전용 스모크만 허용한다.

## 구성

```
e2e/
├── docker-compose.e2e.yml      # Postgres(pgvector) + BE + fake-agent
├── docker-compose.real-agent.yml  # 실제 Agent 오버레이 (LLM 비용 발생)
├── fake-agent/                 # Agent API 계약(app/api.py)만 모사하는 FastAPI 스텁
├── playwright.config.ts
└── tests/
    ├── auth.setup.ts           # 역할별 UI 로그인 → storageState 저장
    ├── auth.spec.ts            # 시나리오 1-2: 로그인/역할 가드
    ├── dashboard.spec.ts       # 시나리오 12
    ├── fees.spec.ts            # 시나리오 11: 연차료 일정
    ├── journey.spec.ts         # 시나리오 3-9: 워크플로 5단계 전체 (serial)
    ├── notifications.spec.ts   # 시나리오 10
    ├── prod-smoke.spec.ts      # 시나리오 14: @prod 읽기 전용
    └── real-agent.spec.ts      # 시나리오 13: @real-agent 스모크
```

## 실행 (로컬)

```bash
cd e2e
npm install && npx playwright install chromium

npm run stack:up        # BE 첫 빌드는 수 분 소요
npx playwright test     # FE dev 서버는 자동 기동 (VITE_USE_MOCK_API=false)

npm run stack:reset     # DB 볼륨 삭제 → 다음 up에서 시드 재생성 (suite 간 클린 리셋)
```

전제: `PatentFlow_FE`에서 `npm install`이 한 번 되어 있어야 한다(웹서버가 `npm run dev` 실행).

### 포트 (로컬 개발 스택과 충돌 방지)
| 구성요소 | e2e | 로컬 개발 |
|---|---|---|
| BE | **18080** | 8080 |
| FE dev | **5174** | 5173 |
| Postgres | **55432** | 5432 |
| fake-agent | 18000 | — |

### 계정 (e2e compose가 bootstrap env로 주입)
| 역할 | 이메일 | 비밀번호 |
|---|---|---|
| 관리자 | admin@syuuk.test | E2eAdmin!2026 |
| 사업부(ICT) | ict.manager@syuuk.test | E2eBusiness!2026 |

## 설계 결정

- **Agent는 스텁**: `fake-agent/`가 `POST /api/v1/ai/patents/{id}/evaluate`, progress, recommend-fields, valuation prompts를 고정 fixture로 응답. LLM 비용 0, 응답 4초(FAKE_AGENT_DELAY_SECONDS) — FE 폴링 UI는 실제로 거친다. `FAKE_AGENT_DEGRADE_FIRST_CALL=true`로 특허별 첫 호출은 degraded 반환 → 분기 활성화 자동 배치가 검토 대상을 소진하지 않게 하고, journey의 수동 생성(2번째 호출)이 성공한다.
- **단일 오리진 프록시**: FE dev 서버(5174)가 `/api`를 BE(18080)로 프록시한다(`VITE_DEV_PROXY_TARGET`). 브라우저가 FE·API를 같은 오리진으로 보므로 크로스 포트 쿠키/CSRF 문제가 사라진다(운영 patentflow.live↔api.patentflow.live 동일 사이트 토폴로지와 유사).
- **mock 강제 off**: `PatentFlow_FE/.env.local`에 `VITE_USE_MOCK_API=true`가 박혀 있고 Vite가 이를 셸 env보다 우선시킨다. `global-setup.ts`가 `.env.test.local`(우선순위 최상위)을 생성하고 `--mode test`로 띄워 실제 BE 모드를 강제한다(teardown이 삭제).
- **메일은 simulate**: BE의 `patentflow.mailing.simulate-delivery=true`(e2e 전용 플래그)가 OAuth2 미연동 상태에서도 draft를 SENT로 기록 → `WAITING_BUSINESS_RESPONSE` 전이 검증 가능. 실제 메일은 한 통도 안 나간다.
- **CSRF off**: BE `patentflow.security.csrf-enabled=false`(e2e 전용). CSRF 토큰 핑퐁 결함([BE-14])이 변형 요청을 간헐 401로 만들어 검증을 불안정하게 하므로 e2e에서만 끈다(운영 기본 true). 레이트리밋도 `PATENTFLOW_RATELIMIT_ENABLED=false`로 끈다(컨텍스트마다 신선 로그인).
- **시드 = 리셋 단위**: `down -v` 후 `up`이면 LocalDemoSeedRunner가 185건 특허+계정을 재시드하고, 기동 직후 QuarterActivationScheduler(ApplicationRunner, 시드 이후)가 검토 대상을 자동 생성한다.
- **journey는 serial**: 워크플로 상태가 누적되므로 한 특허로 5단계를 순서대로 통과한다. 검증 기준은 `patentFlow_docs/be_data_flow.md` §5.

## 현재 상태 (2026-06-12)

`docker compose ... up` 후 `npx playwright test` → **17 passed, 1 skipped**(③-1은 선정 특허가 이미 ICT 배정이면 skip). journey가 NOT_IN_REVIEW→…→NOT_IN_REVIEW 5단계를 끝까지 통과한다.

## 실제 Agent 스모크 (발표 전 1~2회만)

```bash
OPENAI_API_KEY=sk-... docker compose -f docker-compose.e2e.yml -f docker-compose.real-agent.yml up --build -d
npx playwright test --project=real-agent   # 최대 25분
```

## 운영 스모크 (읽기 전용, 발표 당일 아침)

```bash
E2E_BASE_URL=https://patentflow.live \
PROD_ADMIN_EMAIL=... PROD_ADMIN_PASSWORD=... \
npx playwright test --grep @prod
```

⚠️ **변형 시나리오(journey 등)는 절대 운영에 돌리지 않는다** — 발표용 데모 시드가 오염되고 복구 경로가 없다.
