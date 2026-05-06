# FE 연동 전달 프롬프트

아래 내용을 PatentFlow FE 작업자에게 전달하세요.

---

PatentFlow BE의 MVP 연동 API가 준비되었습니다. FE mock 또는 fixture 호출을 아래 BE API 호출로 전환해 주세요.

## 접속 정보

BE 실행:

```bash
cd /Users/geonwook/workspace/final_project/PatentFlow_BE
docker compose up --build
```

API base URL:

```text
http://localhost:8080/api/v1
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

응답 envelope:

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

## 현재 사용 가능한 API

### 특허 목록

`GET /patents`

FR/UI: `FR-001`, `FR-002`, `UI-002`, `UI-003`

Query:

```ts
{
  page?: number; // default 1
  size?: number; // default 20
  keyword?: string;
  departmentId?: string;
  reviewWorkflowStatus?: ReviewWorkflowStatus;
  sort?: "annualFeeDueDate,asc" | "annualFeeDueDate,desc" | "title,asc" | "title,desc" | "managementNumber,asc" | "managementNumber,desc";
}
```

응답: `PageResponse<PatentListItem>`

### 특허 상세

`GET /patents/{patentId}`

FR/UI: `FR-005`, `FR-006`, `FR-007`, `FR-008`, `FR-011`, `FR-012`, `UI-005`

응답: `ApiResponse<PatentDetail>`

중요: `aiEvaluationReport`, `finalDecisionRecord`, `businessOpinion`은 화면에서 분리해서 표시해 주세요. AI 추천은 최종 판단이 아닙니다.

### 특허 등록

`POST /patents`

FR/UI: `FR-003`, `FR-004`, `UI-004`

Request:

```ts
{
  managementNumber: string;
  title: string;
  applicationDate?: string;
  coApplicants?: string;
  country?: string;
  registrationDate?: string;
  applicationNumber?: string;
  registrationNumber?: string;
  expectedExpirationDate?: string;
  source?: string;
  businessArea?: string;
  technologyArea?: string;
  productName?: string;
}
```

응답:

```ts
ApiResponse<{ patentId: string; mode: "CREATED" }>
```

### 특허 수정

`PUT /patents/{patentId}`

FR/UI: `FR-003`, `FR-004`, `UI-004`

Request body는 `POST /patents`와 동일합니다.

응답:

```ts
ApiResponse<{ patentId: string; mode: "UPDATED" }>
```

### 특허 외부 조회

`GET /patents/external-lookup`

FR/UI: `FR-003`, `UI-004`

Query:

```ts
{
  managementNumber: string;
  sourcePriority?: "KIPRIS,GOOGLE_PATENTS" | "KIPRIS" | "GOOGLE_PATENTS";
}
```

응답:

```ts
ApiResponse<{
  managementNumber: string;
  title: string;
  applicationDate: string | null;
  coApplicants: string;
  country: string;
  registrationDate: string | null;
  applicationNumber: string;
  registrationNumber: string;
  expectedExpirationDate: string | null;
  source: "KIPRIS" | "GOOGLE_PATENTS";
} | null>
```

비고: KIPRIS service key가 설정되어 있으면 KIPRIS 우선 조회가 가능합니다. 실패하거나 문서 fixture에 있는 관리번호면 `docs/skax_patents_list.md` 기반 metadata로 fallback됩니다.

### 회사 컨텍스트 추천

`POST /patents/context-suggestions`

FR/UI: `FR-003`, `FR-004`, `UI-004`

Request body는 특허 등록 body와 동일하게 보내면 됩니다.

응답:

```ts
ApiResponse<{
  businessArea: string;
  confidenceText: string;
  reason: string;
  technologyArea: string;
} | null>
```

### 특허 이력

`GET /patents/{patentId}/history`

FR/UI: `FR-013`, `UI-005`, `UI-009`

응답: `ApiResponse<PatentHistory[]>`

### 사업부 체크리스트 항목

`GET /business/checklist-items`

FR/UI: `FR-009`, `UI-005`, `UI-006`

응답: `ApiResponse<BusinessChecklistItem[]>`

### 사업부 의견 제출 이력

`GET /patents/{patentId}/business-submissions`

FR/UI: `FR-009`, `FR-013`, `UI-009`

응답: `ApiResponse<BusinessSubmissionVersion[]>`

### 사업부 의견 제출

`POST /patents/{patentId}/business-submissions`

FR/UI: `FR-009`, `UI-005`, `UI-006`

Request:

```ts
{
  patentId: string;
  evaluatorName?: string;
  evaluatedAt?: string;
  responses: Array<{
    itemId: string;
    score: number | null;
    aiSuggestedScore: number;
    memo?: string;
  }>;
  qualitativeScore: number;
  qualitativeMemo?: string;
  finalOpinion: "MAINTAIN" | "ABANDON";
  finalReason?: string;
  additionalNeeds?: string;
}
```

응답: `ApiResponse<BusinessChecklistSubmissionRequest>`

### 최종 판단 일괄 처리

`POST /patents/executive-approvals/bulk-decision`

FR/UI: `FR-011`, `FR-012`, `UI-005`

Request:

```ts
{
  patentIds: string[];
  decision: ExecutiveApprovalDecision;
}
```

응답:

```ts
ApiResponse<{
  decision: ExecutiveApprovalDecision;
  updatedCount: number;
  updatedPatentIds: string[];
}>
```

### 메일 발송 처리

`POST /mailings/send`

FR/UI: `FR-014`, `FR-015`, `FR-016`, `UI-007`

Request:

```ts
{
  patentIds: string[];
}
```

응답:

```ts
ApiResponse<{
  updatedCount: number;
  updatedPatentIds: string[];
}>
```

현재는 실제 메일 발송이 아니라 해당 특허의 workflow status를 `WAITING_BUSINESS_RESPONSE`로 변경하는 MVP 처리입니다.

## 주요 타입/Enum

```ts
type PatentListItem = {
  patentId: string;
  managementNumber: string;
  applicationNumber: string | null;
  registrationNumber: string | null;
  title: string;
  draftTitle: string | null;
  businessArea: string | null;
  technologyArea: string | null;
  productName: string | null;
  country: string | null;
  coApplicants: string | null;
  applicationDate: string | null;
  registrationDate: string | null;
  expectedExpirationDate: string | null;
  departmentId: string;
  departmentName: string;
  lifecycleStatus: PatentLifecycleStatus;
  reviewWorkflowStatus: ReviewWorkflowStatus;
  annualFeeDueDate: string | null;
  reviewReason: string;
  currentRecommendation: Recommendation;
  businessOpinionDecision: BusinessOpinionDecision | null;
  executiveApprovalDecision: ExecutiveApprovalDecision | null;
  legalActionResult: LegalActionResult | null;
};

type PatentDetail = PatentListItem & {
  summary: {
    plainTextSummary: string;
    problemToSolve: string;
    coreTechnicalPoints: string[];
    rightsSummary: string;
    missingInformation: string[];
  };
  aiEvaluationReport: {
    reportId: string;
    createdAt: string;
    recommendation: Recommendation;
    recommendationReason: string;
    totalScore: number;
    scores: Array<{
      category: EvaluationCategory;
      score: number | null;
      evidence: string;
    }>;
    missingInformation: string[];
  };
  finalDecisionRecord: {
    decisionId: string | null;
    decision: ExecutiveApprovalDecision | null;
    reason: string | null;
    decidedAt: string | null;
  };
  businessOpinion: {
    decision: BusinessOpinionDecision | null;
    reason: string | null;
    submittedAt: string | null;
  };
};

type PatentLifecycleStatus = "ACTIVE" | "ABANDONED" | "SOLD" | "EXPIRED";

type ReviewWorkflowStatus =
  | "NOT_IN_REVIEW_QUARTER"
  | "REVIEW_QUARTER_STARTED"
  | "REPORT_GENERATED"
  | "MAIL_READY"
  | "WAITING_BUSINESS_RESPONSE"
  | "BUSINESS_RESPONSE_RECEIVED"
  | "WAITING_EXECUTIVE_APPROVAL"
  | "APPROVAL_COMPLETED"
  | "LEGAL_ACTION_RECORDED";

type Recommendation = "MAINTAIN" | "REVIEW_AGAIN" | "ABANDON" | "SALES_CANDIDATE" | "HOLD";

type BusinessOpinionDecision = "MAINTAIN" | "ABANDON";

type ExecutiveApprovalDecision =
  | "APPROVED_MAINTAIN"
  | "APPROVED_ABANDON"
  | "APPROVED_SELL"
  | "REJECTED"
  | "REQUEST_CHANGES";

type LegalActionResult = "MAINTAINED" | "ABANDONED" | "SOLD";

type EvaluationCategory = "RIGHTS" | "TECHNOLOGY" | "MARKET" | "LIFECYCLE_ECONOMICS";
```

## 현재 BE 데이터 정책

- 특허 기본 metadata는 PostgreSQL/JPA를 사용합니다.
- 최초 실행 시 DB가 비어 있으면 `docs/skax_patents_list.md`를 seed합니다.
- AI 평가 레포트, workflow 상태, 사업부 제출 이력, 메일 발송은 아직 MVP/mock 성격입니다.
- Agent 응답 DTO, 내부 문서 재평가 결과 구조, AI report DB schema, 파일 저장소/S3, 복잡한 reevaluation workflow는 아직 확정/구현하지 않았습니다.

## FE 요청 사항

- 기존 mock API 중 위 목록에 해당하는 것은 `http://localhost:8080/api/v1`로 전환해 주세요.
- 날짜는 `YYYY-MM-DD`, timestamp는 ISO string으로 처리해 주세요.
- 목록 API는 `page`가 1부터 시작합니다.
- `N/A`는 source data가 없을 때만 사용하고, 사용자가 아직 작성하지 않은 값은 `작성 필요`, `대기 중`, `의견 대기` 계열 copy를 유지해 주세요.
- AI 추천, 최종 판단, 사업부 의견은 화면과 상태 관리에서 섞지 말아 주세요.
- 현재 평가 축은 `RIGHTS`, `TECHNOLOGY`, `MARKET`, `LIFECYCLE_ECONOMICS` 네 가지만 사용해 주세요.
