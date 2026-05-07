# api_docs_pass.md — PatentFlow BE 전달용 API 연동 문서

작성일: 2026-05-06  
대상: Backend 개발자, AI Serving 개발자, FE 연동 담당자  
목적: 비어 있는 BE 프로젝트에서 PatentFlow FE와 우선 연동할 API 계약과 구현 순서를 명확히 정리한다.

## 1. 연동 원칙

- Base URL은 `/api/v1`을 사용한다.
- FE는 `VITE_API_BASE_URL`이 있으면 실제 BE API를 호출하고, 없으면 mock 데이터를 사용한다.
- BE가 비어 있는 상태에서는 DB보다 HTTP 계약을 먼저 맞춘다.
- 초기 BE는 in-memory fixture 또는 JSON fixture 응답으로 시작해도 된다.
- AI 평가 결과는 최종 판단이 아니다. 반드시 `AI 특허 평가 레포트`와 `최종 판단`을 분리한다.
- 현재 평가축은 `RIGHTS`, `TECHNOLOGY`, `MARKET`, `BUSINESS_ALIGNMENT`, `LIFECYCLE_ECONOMICS`를 사용한다.
- `BUSINESS_RELEVANCE`, `STRATEGIC_VALUE`, `RIGHT_SCOPE`, `MATURITY`, `COST_EFFECTIVENESS` 등 과거 평가축은 사용하지 않는다.
- 날짜는 `yyyy-mm-dd`, datetime은 ISO 8601 형식과 `+09:00` 타임존을 권장한다.

## 2. 공통 응답 형식

단건 응답:

```json
{
  "data": {},
  "message": "OK",
  "timestamp": "2026-05-06T10:00:00+09:00"
}
```

목록 응답:

```json
{
  "data": [],
  "page": {
    "page": 1,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  },
  "message": "OK",
  "timestamp": "2026-05-06T10:00:00+09:00"
}
```

에러 응답:

```json
{
  "code": "PATENT_NOT_FOUND",
  "message": "특허 정보를 찾을 수 없습니다.",
  "details": {},
  "timestamp": "2026-05-06T10:00:00+09:00"
}
```

## 3. 공통 Enum

FE 최신 기준은 `src/constants/status.ts`와 `SHARED_AGENT.md`다. BE는 아래 문자열 enum을 우선 맞춘다.

```ts
type PatentLifecycleStatus =
  | "ACTIVE"
  | "ABANDONED"
  | "SOLD"
  | "EXPIRED";

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

type Recommendation =
  | "MAINTAIN"
  | "REVIEW_AGAIN"
  | "ABANDON"
  | "SALES_CANDIDATE"
  | "HOLD";

type BusinessOpinionDecision =
  | "MAINTAIN"
  | "ABANDON";

type ExecutiveApprovalDecision =
  | "APPROVED_MAINTAIN"
  | "APPROVED_ABANDON"
  | "APPROVED_SELL"
  | "REJECTED"
  | "REQUEST_CHANGES";

type LegalActionResult =
  | "MAINTAINED"
  | "ABANDONED"
  | "SOLD";

type EvaluationCategory =
  | "RIGHTS"
  | "TECHNOLOGY"
  | "MARKET"
  | "BUSINESS_ALIGNMENT"
  | "LIFECYCLE_ECONOMICS";
```

## 4. MVP 구현 우선순위

BE가 비어 있으므로 아래 순서대로 구현한다.

| 우선순위 | API | Method | 목적 | 관련 FR |
|---:|---|---|---|---|
| 1 | `/api/v1/patents` | GET | 특허 목록 조회, 검색, 필터링, 정렬 | FR-001, FR-002 |
| 2 | `/api/v1/patents/{patentId}` | GET | 특허 상세, 요약, AI 평가 레포트, 최종 판단 조회 | FR-005, FR-006, FR-007, FR-008, FR-011, FR-012 |
| 3 | `/api/v1/patents/{patentId}/history` | GET | 평가/판단 이력 조회 | FR-013 |
| 4 | `/api/v1/patents/executive-approvals/bulk-decision` | POST | 결재 대기 특허 일괄 최종 판단 처리 | FR-011, FR-012 |
| 5 | `/api/v1/mailings/send` | POST | 사업부 검토 요청 메일 발송 처리 | FR-014, FR-015, FR-016 |
| 6 | `/api/v1/patents` | POST | 특허 기본 정보 등록 | FR-003, FR-004 |
| 7 | `/api/v1/patents/{patentId}` | PUT | 특허 기본 정보 수정 | FR-003, FR-004 |

초기에는 1~3번만 실제 HTTP로 붙어도 Admin Dashboard, Patent Management, Patent Detail의 핵심 연동이 가능하다.

## 5. API 상세 명세

### 5.1 특허 목록 조회

```http
GET /api/v1/patents?page=1&size=20&keyword=AI&reviewWorkflowStatus=REPORT_GENERATED&departmentId=DEPT-RND&sort=annualFeeDueDate,asc
```

Query parameters:

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `page` | number | N | 1부터 시작 |
| `size` | number | N | 기본 20 |
| `keyword` | string | N | 제목, 관리번호, 출원번호, 등록번호 검색 |
| `departmentId` | string | N | 부서 필터 |
| `reviewWorkflowStatus` | ReviewWorkflowStatus | N | 검토 workflow 상태 필터 |
| `sort` | string | N | 예: `annualFeeDueDate,asc` |

Response:

```json
{
  "data": [
    {
      "patentId": "PAT-2026-0001",
      "managementNumber": "SKAX-001",
      "applicationNumber": "10-2021-0000001",
      "registrationNumber": "10-2500000",
      "title": "테스트용 AI 특허",
      "draftTitle": "테스트용 AI 특허 가제",
      "businessArea": "AI",
      "technologyArea": "자연어처리",
      "productName": "PatentFlow",
      "country": "KR",
      "coApplicants": "없음",
      "applicationDate": "2021-05-06",
      "registrationDate": "2023-05-06",
      "expectedExpirationDate": "2041-05-06",
      "departmentId": "DEPT-RND",
      "departmentName": "R&D본부",
      "lifecycleStatus": "ACTIVE",
      "reviewWorkflowStatus": "REPORT_GENERATED",
      "annualFeeDueDate": "2026-05-06",
      "reviewReason": "연차료 납부 검토 시점 도래",
      "currentRecommendation": "REVIEW_AGAIN",
      "businessOpinionDecision": null,
      "executiveApprovalDecision": null,
      "legalActionResult": null
    }
  ],
  "page": {
    "page": 1,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "message": "OK",
  "timestamp": "2026-05-06T10:00:00+09:00"
}
```

### 5.2 특허 상세 조회

```http
GET /api/v1/patents/{patentId}
```

Response:

```json
{
  "data": {
    "patentId": "PAT-2026-0001",
    "managementNumber": "SKAX-001",
    "applicationNumber": "10-2021-0000001",
    "registrationNumber": "10-2500000",
    "title": "테스트용 AI 특허",
    "draftTitle": "테스트용 AI 특허 가제",
    "businessArea": "AI",
    "technologyArea": "자연어처리",
    "productName": "PatentFlow",
    "country": "KR",
    "coApplicants": "없음",
    "applicationDate": "2021-05-06",
    "registrationDate": "2023-05-06",
    "expectedExpirationDate": "2041-05-06",
    "departmentId": "DEPT-RND",
    "departmentName": "R&D본부",
    "lifecycleStatus": "ACTIVE",
    "reviewWorkflowStatus": "REPORT_GENERATED",
    "annualFeeDueDate": "2026-05-06",
    "reviewReason": "연차료 납부 검토 시점 도래",
    "currentRecommendation": "REVIEW_AGAIN",
    "businessOpinionDecision": null,
    "executiveApprovalDecision": null,
    "legalActionResult": null,
    "summary": {
      "summaryText": "특허 내용을 비전문가도 이해할 수 있도록 요약한 테스트 데이터입니다.",
      "problemSolved": "기존 업무 검토 과정의 반복 작업을 줄이는 문제를 다룹니다.",
      "coreTechnicalPoints": ["문서 요약", "근거 추출", "평가 기준 매핑"],
      "claimsSummary": "청구항의 주요 권리 범위를 요약한 테스트 데이터입니다.",
      "missingFields": ["시장 규모 자료", "실제 제품 적용 여부"]
    },
    "aiEvaluationReport": {
      "evaluationId": "EVAL-2026-0001",
      "createdAt": "2026-05-06T10:00:00+09:00",
      "recommendation": "REVIEW_AGAIN",
      "recommendationText": "추가 자료 확인 후 유지 여부를 재검토하는 것이 적절합니다.",
      "totalScore": 72,
      "scores": [
        {
          "category": "RIGHTS",
          "score": 70,
          "evidenceSummary": "청구항 범위는 확인되나 일부 권리 범위 비교 자료가 부족합니다."
        },
        {
          "category": "TECHNOLOGY",
          "score": 78,
          "evidenceSummary": "명세서상 기술적 차별 요소가 확인됩니다."
        },
        {
          "category": "MARKET",
          "score": null,
          "evidenceSummary": "시장 규모 자료가 부족하여 추가 확인이 필요합니다."
        },
        {
          "category": "BUSINESS_ALIGNMENT",
          "score": 72,
          "evidenceSummary": "관련사업 분야와 기술 영역은 연결되지만 실제 제품 적용 여부는 추가 확인이 필요합니다."
        },
        {
          "category": "LIFECYCLE_ECONOMICS",
          "score": 68,
          "evidenceSummary": "유지 비용 대비 활용 가능성 검토가 필요합니다."
        }
      ],
      "missingInformation": ["시장 규모 자료", "제품 적용 여부"]
    },
    "finalDecisionRecord": {
      "decisionId": null,
      "decision": null,
      "reason": null,
      "decidedAt": null
    },
    "businessOpinion": {
      "opinion": null,
      "comment": null,
      "submittedAt": null
    }
  },
  "message": "OK",
  "timestamp": "2026-05-06T10:00:00+09:00"
}
```

### 5.3 평가/판단 이력 조회

```http
GET /api/v1/patents/{patentId}/history
```

Response:

```json
{
  "data": [
    {
      "historyId": "HIST-2026-0001",
      "type": "AI_EVALUATION_CREATED",
      "title": "AI 평가 레포트 생성",
      "description": "1차 AI 특허 평가 레포트가 생성되었습니다.",
      "actorName": "AI Evaluation Service",
      "createdAt": "2026-05-06T10:00:00+09:00"
    },
    {
      "historyId": "HIST-2026-0002",
      "type": "HUMAN_DECISION_UPDATED",
      "title": "최종 판단 기록",
      "description": "관리자 최종 판단이 기록되었습니다.",
      "actorName": "관리자",
      "createdAt": "2026-05-06T11:00:00+09:00"
    }
  ],
  "message": "OK",
  "timestamp": "2026-05-06T11:00:00+09:00"
}
```

### 5.4 결재 대기 특허 일괄 최종 판단

```http
POST /api/v1/patents/executive-approvals/bulk-decision
```

Request:

```json
{
  "patentIds": ["PAT-2026-0001", "PAT-2026-0002"],
  "decision": "APPROVED_MAINTAIN"
}
```

Response:

```json
{
  "data": {
    "decision": "APPROVED_MAINTAIN",
    "updatedCount": 2,
    "updatedPatentIds": ["PAT-2026-0001", "PAT-2026-0002"]
  },
  "message": "OK",
  "timestamp": "2026-05-06T11:10:00+09:00"
}
```

### 5.5 사업부 검토 요청 메일 발송 처리

```http
POST /api/v1/mailings/send
```

Request:

```json
{
  "patentIds": ["PAT-2026-0001", "PAT-2026-0002"]
}
```

Response:

```json
{
  "data": {
    "updatedCount": 2,
    "updatedPatentIds": ["PAT-2026-0001", "PAT-2026-0002"]
  },
  "message": "OK",
  "timestamp": "2026-05-06T11:20:00+09:00"
}
```

### 5.6 특허 외부 검색

초기에는 mock 또는 고정 fixture로 응답해도 된다. 실제 KIPRIS/Google Patents 연동은 후순위다.

```http
GET /api/v1/patents/external-lookup?managementNumber=SKAX-001&sourcePriority=KIPRIS,GOOGLE_PATENTS
```

Response:

```json
{
  "data": {
    "managementNumber": "SKAX-001",
    "title": "테스트용 AI 특허",
    "applicationDate": "2021-05-06",
    "coApplicants": "없음",
    "country": "KR",
    "registrationDate": "2023-05-06",
    "applicationNumber": "10-2021-0000001",
    "registrationNumber": "10-2500000",
    "expectedExpirationDate": "2041-05-06",
    "source": "KIPRIS"
  },
  "message": "OK",
  "timestamp": "2026-05-06T11:30:00+09:00"
}
```

### 5.7 특허 등록

```http
POST /api/v1/patents
```

Request:

```json
{
  "managementNumber": "SKAX-001",
  "title": "테스트용 AI 특허",
  "applicationDate": "2021-05-06",
  "coApplicants": "없음",
  "country": "KR",
  "registrationDate": "2023-05-06",
  "applicationNumber": "10-2021-0000001",
  "registrationNumber": "10-2500000",
  "expectedExpirationDate": "2041-05-06",
  "source": "KIPRIS",
  "businessArea": "AI",
  "technologyArea": "자연어처리",
  "productName": "PatentFlow"
}
```

Response:

```json
{
  "data": {
    "patentId": "PAT-2026-0001",
    "mode": "CREATED"
  },
  "message": "OK",
  "timestamp": "2026-05-06T11:40:00+09:00"
}
```

### 5.8 특허 수정

```http
PUT /api/v1/patents/{patentId}
```

Request는 `POST /api/v1/patents`와 동일한 payload를 사용한다.

Response:

```json
{
  "data": {
    "patentId": "PAT-2026-0001",
    "mode": "UPDATED"
  },
  "message": "OK",
  "timestamp": "2026-05-06T11:50:00+09:00"
}
```

### 5.9 회사 컨텍스트 추천

초기에는 keyword matching 기반 mock 응답으로 충분하다.

```http
POST /api/v1/patents/context-suggestions
```

Request:

```json
{
  "title": "테스트용 AI 특허",
  "summaryText": "문서 요약과 근거 추출 관련 특허"
}
```

Response:

```json
{
  "data": {
    "businessArea": "AI",
    "technologyArea": "자연어처리",
    "confidenceText": "높음",
    "reason": "제목과 요약에 AI/자연어처리 관련 키워드가 포함되어 있습니다."
  },
  "message": "OK",
  "timestamp": "2026-05-06T12:00:00+09:00"
}
```

## 6. 사업부 기능 확장 API

관리자 핵심 API 이후 구현한다.

| API | Method | 목적 | 관련 FR |
|---|---|---|---|
| `/api/v1/business/checklist-items` | GET | 사업부 체크리스트 항목 조회 | FR-009 |
| `/api/v1/patents/{patentId}/business-submissions` | GET | 사업부 제출 이력 조회 | FR-009, FR-013 |
| `/api/v1/patents/{patentId}/business-submissions` | POST | 사업부 의견/체크리스트 제출 | FR-009 |
| `/api/v1/patents/{patentId}/internal-documents` | POST | 내부 문서 업로드 | FR-010 |
| `/api/v1/patents/{patentId}/reevaluation` | POST | 내부 문서 반영 재평가 요청 | FR-006, FR-010 |

사업부 체크리스트 주의사항:

- 최종 의견은 `MAINTAIN` 또는 `ABANDON`만 사용한다.
- 체크리스트 점수와 AI 평가 점수는 합산하지 않는다.
- 사업부 제출 이력 상세는 일반 특허 상세와 별도 화면에서 사용한다.

## 7. 알림 API

현재 FE는 아래 API를 호출할 수 있다. 초기에는 fixture 응답으로 시작해도 된다.

| API | Method | 목적 |
|---|---|---|
| `/api/v1/notifications?role=ADMIN` | GET | 역할별 알림 조회 |
| `/api/v1/notifications/{notificationId}/read-state` | PATCH | 알림 읽음/읽지 않음 상태 변경 |

읽음 상태 변경 Request:

```json
{
  "isRead": true
}
```

## 8. 최종 발표 전 확장 API

| API | Method | 목적 | 관련 FR |
|---|---|---|---|
| `/api/v1/mailings/preview` | POST | 메일 미리보기 | FR-015 |
| `/api/v1/mailings/history` | GET | 메일 발송 이력 조회 | FR-016 |
| `/api/v1/recipient-mappings` | GET/PUT | 부서별 수신자 매핑 조회/수정 | FR-014 |
| `/api/v1/sales-candidates` | GET | 매각 후보 목록 조회 | FR-017 |
| `/api/v1/sales-candidates` | POST | 포기 특허를 매각 후보로 등록 | FR-017 |
| `/api/v1/sales-candidates/{candidateId}` | PUT | 매각 후보 상태 수정 | FR-017 |
| `/api/v1/evaluation-criteria` | GET/PUT | 평가 기준 조회/수정 | FR-006, FR-007 |

## 9. BE 초기 구현 제안

1. Spring Boot 프로젝트 생성
2. CORS 설정
3. 공통 응답 envelope와 공통 에러 응답 작성
4. enum과 DTO 작성
5. `docs/skax_patents_list.md` 기반 fixture 작성
6. `GET /api/v1/patents` 구현
7. `GET /api/v1/patents/{patentId}` 구현
8. `GET /api/v1/patents/{patentId}/history` 구현
9. FE에서 `VITE_API_BASE_URL`로 실제 호출 확인
10. 이후 DB, 인증, AI Serving, 메일 발송 순서로 확장

## 10. FE 연동 체크리스트

- FE `.env`에 `VITE_API_BASE_URL=http://localhost:8080` 설정
- BE CORS에서 FE dev server origin 허용
- `GET /api/v1/patents`가 `PaginatedApiEnvelope<PatentListItem>` 형태로 응답
- `GET /api/v1/patents/{patentId}`가 `PatentDetail` 형태로 응답
- `aiEvaluationReport` 필드명을 사용하고, 과거 이름인 `aiEvaluationDraft`는 사용하지 않음
- `finalDecisionRecord` 필드명을 사용하고, AI report와 분리
- `reviewWorkflowStatus`를 사용하고, 과거 단일 `status` enum에 모든 상태를 섞지 않음
- 평가 category에 `BUSINESS_RELEVANCE`를 포함하지 않음

## 11. 관련 문서

- `SHARED_AGENT.md`: FE/BE/AI 공통 개발 규칙
- `AGENTS.md`: FE 전용 작업 규칙
- `docs/api_priority.md`: API 우선순위 참고 문서
- `docs/need_api.md`: 전체 API 초안 참고 문서
- `docs/patent_evaluation_criteria.md`: 평가 기준 원본
- `docs/skax_patents_list.md`: 특허 metadata fixture 원본
