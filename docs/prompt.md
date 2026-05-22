# PatentFlow BE-FE Integration Notes

이 문서는 현재 BE 구현과 FE 연동 계약을 요약한다. 예전 `FR-001` 체계와 임원 승인 workflow는 더 이상 공식 계약으로 사용하지 않는다.

## API Base

```text
http://localhost:8080/api/v1
```

## Response Envelope

```ts
type ApiResponse<T> = {
  data: T | null;
  message: "OK";
  timestamp: string;
};

type PageResponse<T> = {
  data: T[];
  page: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
  message: "OK";
  timestamp: string;
};
```

## Current Workflow Status Contract

`ReviewWorkflowStatus`는 현재 아래 6개 값만 사용한다.

```ts
type ReviewWorkflowStatus =
  | "NOT_IN_REVIEW_QUARTER"
  | "REVIEW_QUARTER_STARTED"
  | "MAIL_READY"
  | "WAITING_BUSINESS_RESPONSE"
  | "BUSINESS_RESPONSE_RECEIVED"
  | "LEGAL_ACTION_RECORDED";
```

`REPORT_GENERATED`는 현재 workflow 상태로 사용하지 않는다. AI 평가 레포트 생성이 완료되면 특허 workflow 상태는 바로 `MAIL_READY`가 된다.

## Main API Traceability

| API | Method | Description | Related FR | Related UI |
|---|---|---|---|---|
| `/auth/login` | POST | 로그인 및 세션 생성 | `FR-COM-01` | `UI-COM-01` |
| `/auth/me` | GET | 현재 사용자 확인 | `FR-COM-01` | `UI-COM-01` |
| `/patents` | GET | 특허 목록/검색/필터/정렬/페이지 조회 | `FR-LEGAL-01`, `FR-LEGAL-02` | `UI-COM-02`, `UI-LEGAL-01`, `UI-LEGAL-02`, `UI-BUS-01` |
| `/patents/{patentId}` | GET | 특허 상세 조회 | `FR-LEGAL-05`~`FR-LEGAL-11`, `FR-BUS-01` | `UI-LEGAL-04`, `UI-BUS-02` |
| `/patents` | POST | 특허 기본 정보 등록 | `FR-LEGAL-03` | `UI-LEGAL-02` |
| `/patents/{patentId}` | PUT | 특허 기본 정보 수정 | `FR-LEGAL-03`, `FR-LEGAL-04` | `UI-LEGAL-03` |
| `/patents/external-lookup` | GET | 외부/문서 기반 특허 정보 조회 | `FR-LEGAL-03` | `UI-LEGAL-02`, `UI-LEGAL-03` |
| `/patents/context-suggestions` | POST | 사업/기술 분야 추천 | `FR-LEGAL-04` | `UI-LEGAL-03` |
| `/patents/review-targets` | GET | 분기/날짜 범위/국가 기준 검토 대상 조회 | `FR-LEGAL-22`, `FR-LEGAL-24` | `UI-LEGAL-01`, `UI-COM-02`, `UI-BUS-01` |
| `/patents/{patentId}/request-ai-report` | POST | AI 평가 레포트 생성, 성공 시 `MAIL_READY` 전환 | `FR-LEGAL-06`, `FR-LEGAL-18` | `UI-LEGAL-04`, `UI-BUS-02` |
| `/patents/{patentId}/history` | GET | 평가 및 판단 이력 조회 | `FR-LEGAL-11` | `UI-LEGAL-04`, `UI-BUS-05` |
| `/business/checklist-items` | GET | 사업부 체크리스트 항목 조회 | `FR-BUS-04` | `UI-BUS-03` |
| `/patents/{patentId}/business-submissions` | GET | 사업부 제출 이력 조회 | `FR-BUS-01`, `FR-LEGAL-11` | `UI-BUS-04`, `UI-BUS-05` |
| `/patents/{patentId}/business-submissions` | POST | 사업부 의견 및 체크리스트 제출 | `FR-BUS-01` | `UI-BUS-03` |
| `/patents/{patentId}/final-decision` | POST | 최종 처리 결과 기록 | `FR-LEGAL-10`, `FR-LEGAL-19` | `UI-LEGAL-04` |
| `/patents/{patentId}/final-decision` | PATCH | 최종 판단 수정 및 취소 | `FR-LEGAL-20` | `UI-LEGAL-04` |
| `/mailings/send` | POST | 사업부 검토 요청 메일 발송 | `FR-LEGAL-13`, `FR-LEGAL-14` | `UI-LEGAL-05` |
| `/mailings/history` | GET | 메일 발송 이력 조회 | `FR-LEGAL-14` | `UI-LEGAL-04`, `UI-LEGAL-05` |
| `/settings/review-schedule` | GET/PATCH | 회신 기한, 메일 발송 기준 개월 수, 분기별 예상 발송일 설정/조회 | `FR-LEGAL-23` | `UI-LEGAL-05`, `UI-LEGAL-07` |
| `/settings/classifications` | GET/POST/PATCH/DELETE | 사업 분류 및 기술 분류 기준값 관리 | `FR-LEGAL-25` | `UI-LEGAL-03`, `UI-LEGAL-07` |
| `/annual-fees/schedule` | GET/PATCH | 국가별 미래 연차료 납부 예정일 조회 및 조정 | `FR-LEGAL-24` | `UI-LEGAL-01`, `UI-LEGAL-07` |
| `/departments` | GET/PATCH | 부서 및 메일 수신자 설정 | `FR-LEGAL-12`, `FR-LEGAL-16` | `UI-LEGAL-07`, `UI-LEGAL-08` |
| `/notifications` | GET | 알림 목록 조회 | `FR-COM-02` | `UI-COM-03` |
| `/notifications/{notificationId}/read` | PATCH | 알림 읽음 상태 변경 | `FR-COM-02` | `UI-COM-03` |

## Quarter And Mailing Contract

- Quarter query values are `Q1`, `Q2`, `Q3`, and `Q4`; date-range query values should use ISO dates.
- Quarter ranges are calendar ranges: Q1 is January 1 through March last day, Q2 is April 1 through June last day, Q3 is July 1 through September last day, and Q4 is October 1 through December last day.
- `businessResponseDueDate` represents business-facing `회신 기한`; `legalDueDate` or another explicit internal field should represent administrator/legal `실제 마감 기한`.
- `mailLeadMonths` defaults to `2`, but must be stored as an administrator setting.
- Settings responses should include calculated quarter send dates so the frontend can show Q1 through Q4 mail schedules without duplicating business logic.
- Mailing preview, send request payloads, and mailing history responses must include `originalPatentUrl` for each patent.
- Patent/review target DTOs should include country and annual-fee schedule fields needed by country dashboards: `countryCode`, `isDomesticPatent`, `annualFeeBaseDate`, `nextAnnualFeeDueDate`, `adjustedAnnualFeeDueDate`, and adjustment reason/history when present.

## Current Evaluation Categories

```ts
type EvaluationCategory =
  | "RIGHTS"
  | "TECHNOLOGY"
  | "MARKET"
  | "BUSINESS_ALIGNMENT";
```

AI 평가는 의사결정 지원 레포트이며, 최종 판단은 관리자/법무 사용자가 별도로 기록한다.
