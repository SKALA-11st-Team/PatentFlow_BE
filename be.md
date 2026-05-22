# PatentFlow BE 인수인계 문서

작성일: 2026-05-07  
작성 목적: FE 담당자가 임시로 구현한 BE 작업 범위, 현재 API 인터페이스, 구현 상태, 후속 구현 필요 사항을 BE 담당자에게 전달한다.

## 1. 전달 요약

- 현재 BE는 Spring Boot 3.5.6, Java 17 기반 MVP API 서버다.
- Swagger UI는 `/swagger-ui.html`, API base path는 `/api/v1`이다.
- FE 연동 우선 API를 중심으로 구현했다.
- 공식 demo 특허 metadata는 `docs/skax_patents_list.md`에서 읽어 DB에 seed한다.
- 특허 기본 metadata는 JPA `PatentMetadataEntity`로 PostgreSQL에 저장한다.
- AI 평가 레포트, 요약, workflow 상태, 사업부 제출 이력, 메일 발송 상태 변경은 아직 fixture/mock 성격이 강하다.
- AI 평가 결과와 최종 판단은 응답 모델에서 분리했다.
- 평가축은 `RIGHTS`, `TECHNOLOGY`, `MARKET`, `BUSINESS_ALIGNMENT`, `LIFECYCLE_ECONOMICS`를 사용한다.
- `BUSINESS_ALIGNMENT`는 사업 연계성 평가축으로 사용한다.

## 2. 구현한 주요 기능

| 영역 | 구현 내용 | 관련 FR/UI |
|---|---|---|
| 특허 목록 | 검색, 부서 필터, workflow 상태 필터, 정렬, 페이징 | FR-001, FR-002 / UI-002, UI-003 |
| 특허 등록/수정 | 특허 기본 metadata와 회사 컨텍스트 등록/수정 | FR-003, FR-004 / UI-004 |
| 외부 특허 조회 | KIPRIS/Google Patents client 구조와 fixture fallback | FR-003 / UI-004 |
| 컨텍스트 추천 | 입력 특허명/제품/기술 키워드 기반 mock 추천 | FR-003, FR-004 / UI-004 |
| 특허 상세 | metadata, 요약, AI 평가 레포트, 사업부 의견, 최종 판단 분리 조회 | FR-005~FR-008, FR-011, FR-012 / UI-005 |
| 이력 조회 | AI 평가/최종 판단 mock history 조회 | FR-013 / UI-005, UI-009 |
| 사업부 체크리스트 | 체크리스트 항목 조회, 제출, 제출 버전 이력 조회 | FR-009, FR-013 / UI-005, UI-006, UI-009 |
| 메일 발송 처리 | `MAIL_READY` 특허만 `WAITING_BUSINESS_RESPONSE`로 상태 변경 | FR-014~FR-016 / UI-007 |
| 최종 판단 | 일괄 결재 처리 및 legal action result 매핑 | FR-011, FR-012 / UI-005 |
| 공통 응답/에러 | `ApiResponse`, `PageResponse`, `ErrorResponse`, global exception handler | 공통 |

## 3. 현재 프로젝트 구조

```text
src/main/java/com/syuuk/patentflow
├── common
│   ├── config          # CORS, OpenAPI 설정
│   ├── error           # ErrorCode, ErrorResponse, GlobalExceptionHandler
│   └── response        # ApiResponse, PageResponse
├── patent
│   ├── client          # KIPRIS/Google Patents lookup client
│   ├── controller      # PatentController
│   ├── domain          # PatentMetadataEntity
│   ├── dto             # 특허 API DTO 및 enum
│   ├── repository      # PatentMetadataRepository
│   └── service         # PatentFixtureService
├── business
│   ├── controller      # BusinessController
│   ├── dto             # 사업부 체크리스트 DTO
│   └── service         # BusinessFixtureService
└── mailing
    ├── controller      # MailingController
    └── dto             # 메일 발송 DTO
```

## 4. 실행 및 환경

```bash
docker compose up --build
```

```bash
mvn test
```

기본 URL:

```text
http://localhost:8080/api/v1
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

주요 환경 변수:

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/patentflow` | PostgreSQL 연결 URL |
| `SPRING_DATASOURCE_USERNAME` | `patentflow` | DB 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | `patentflow` | DB 비밀번호 |
| `PATENTFLOW_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | FE 허용 origin |
| `PATENTFLOW_KIPRIS_ENABLED` | `false` | KIPRIS lookup 사용 여부 |
| `PATENTFLOW_KIPRIS_SERVICE_KEY` | 빈 값 | KIPRISPlus service key |
| `PATENTFLOW_GOOGLE_PATENTS_ENABLED` | `true` | Google Patents fallback 사용 여부 |

## 5. 공통 응답 계약

단건 응답:

```json
{
  "data": {},
  "message": "OK",
  "timestamp": "2026-05-07T10:00:00+09:00"
}
```

페이지 응답:

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
  "timestamp": "2026-05-07T10:00:00+09:00"
}
```

에러 응답:

```json
{
  "code": "INVALID_REQUEST",
  "message": "요청 값을 확인해주세요.",
  "details": {},
  "timestamp": "2026-05-07T10:00:00+09:00"
}
```

현재 에러 코드:

| Code | HTTP | 의미 |
|---|---:|---|
| `PATENT_NOT_FOUND` | 404 | 특허 정보를 찾을 수 없음 |
| `INVALID_REQUEST` | 400 | 요청 값 오류 또는 validation 실패 |
| `INVALID_WORKFLOW_STATUS` | 409 | 현재 workflow 단계에서 수행 불가 |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |

## 6. OpenAPI 인터페이스 요약

```yaml
openapi: 3.0.3
info:
  title: PatentFlow BE API
  version: 0.0.1-mvp
servers:
  - url: http://localhost:8080/api/v1
paths:
  /patents:
    get:
      summary: 특허 목록 검색/필터링/정렬/페이징
      parameters:
        - name: page
          in: query
          schema: { type: integer, default: 1, minimum: 1 }
        - name: size
          in: query
          schema: { type: integer, default: 20, minimum: 1, maximum: 20 }
        - name: keyword
          in: query
          schema: { type: string }
        - name: departmentId
          in: query
          schema: { type: string }
        - name: reviewWorkflowStatus
          in: query
          schema: { $ref: '#/components/schemas/ReviewWorkflowStatus' }
        - name: sort
          in: query
          description: annualFeeDueDate asc default, title,asc, managementNumber,desc 등
          schema: { type: string }
      responses:
        '200':
          description: OK
    post:
      summary: 특허 기본 정보와 회사 컨텍스트 등록
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/PatentUpsertRequest' }
      responses:
        '200':
          description: OK
  /patents/external-lookup:
    get:
      summary: 관리번호/출원번호/등록번호 기반 외부 특허 조회
      parameters:
        - name: managementNumber
          in: query
          required: true
          schema: { type: string }
        - name: sourcePriority
          in: query
          description: 예: KIPRIS,GOOGLE_PATENTS
          schema: { type: string }
      responses:
        '200':
          description: OK. 미매칭 시 data null
  /patents/context-suggestions:
    post:
      summary: 특허명/제품/기술 기반 회사 컨텍스트 추천
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/PatentContextSuggestionRequest' }
      responses:
        '200':
          description: OK. 추천 불가 시 data null
  /patents/{patentId}:
    get:
      summary: 특허 상세, 요약, AI 평가 레포트, 최종 판단 분리 조회
      parameters:
        - name: patentId
          in: path
          required: true
          schema: { type: string }
      responses:
        '200':
          description: OK
        '404':
          description: PATENT_NOT_FOUND
    put:
      summary: 특허 기본 정보와 회사 컨텍스트 수정
      parameters:
        - name: patentId
          in: path
          required: true
          schema: { type: string }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/PatentUpsertRequest' }
      responses:
        '200':
          description: OK
        '404':
          description: PATENT_NOT_FOUND
  /patents/{patentId}/history:
    get:
      summary: 평가/판단 이력 조회
      parameters:
        - name: patentId
          in: path
          required: true
          schema: { type: string }
      responses:
        '200':
          description: OK
        '404':
          description: PATENT_NOT_FOUND
  /business/checklist-items:
    get:
      summary: 사업부 체크리스트 항목 조회
      responses:
        '200':
          description: OK
  /patents/{patentId}/business-submissions:
    get:
      summary: 특허별 사업부 제출 이력 조회
      parameters:
        - name: patentId
          in: path
          required: true
          schema: { type: string }
      responses:
        '200':
          description: OK
        '404':
          description: PATENT_NOT_FOUND
    post:
      summary: 사업부 의견/체크리스트 제출
      parameters:
        - name: patentId
          in: path
          required: true
          schema: { type: string }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/BusinessChecklistSubmissionRequest' }
      responses:
        '200':
          description: OK
        '400':
          description: INVALID_REQUEST
        '404':
          description: PATENT_NOT_FOUND
  /mailings/send:
    post:
      summary: 사업부 검토 요청 메일 발송 처리
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/MailingSendRequest' }
      responses:
        '200':
          description: OK
  /patents/executive-approvals/bulk-decision:
    post:
      summary: 특허 최종 의사결정 일괄 반영
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ExecutiveApprovalBulkDecisionRequest' }
      responses:
        '200':
          description: OK
        '409':
          description: INVALID_WORKFLOW_STATUS
components:
  schemas:
    PatentLifecycleStatus:
      type: string
      enum: [ACTIVE, ABANDONED, SOLD, EXPIRED]
    ReviewWorkflowStatus:
      type: string
      enum:
        - NOT_IN_REVIEW_QUARTER
        - REVIEW_QUARTER_STARTED
        - MAIL_READY
        - WAITING_BUSINESS_RESPONSE
        - BUSINESS_RESPONSE_RECEIVED
        - LEGAL_ACTION_RECORDED
    Recommendation:
      type: string
      enum: [MAINTAIN, REVIEW_AGAIN, ABANDON, SALES_CANDIDATE, HOLD]
    BusinessOpinionDecision:
      type: string
      enum: [MAINTAIN, ABANDON]
    ExecutiveApprovalDecision:
      type: string
      enum: [APPROVED_MAINTAIN, APPROVED_ABANDON, APPROVED_SELL, REJECTED, REQUEST_CHANGES]
    LegalActionResult:
      type: string
      enum: [MAINTAINED, ABANDONED, SOLD]
    EvaluationCategory:
      type: string
      enum: [RIGHTS, TECHNOLOGY, MARKET, BUSINESS_ALIGNMENT, LIFECYCLE_ECONOMICS]
    PatentUpsertRequest:
      type: object
      required: [managementNumber, title]
      properties:
        managementNumber: { type: string }
        title: { type: string }
        applicationDate: { type: string, format: date, nullable: true }
        coApplicants: { type: string, nullable: true }
        country: { type: string, nullable: true }
        registrationDate: { type: string, format: date, nullable: true }
        applicationNumber: { type: string, nullable: true }
        registrationNumber: { type: string, nullable: true }
        expectedExpirationDate: { type: string, format: date, nullable: true }
        source: { type: string, nullable: true }
        businessArea: { type: string, nullable: true }
        technologyArea: { type: string, nullable: true }
        productName: { type: string, nullable: true }
    PatentContextSuggestionRequest:
      type: object
      properties:
        managementNumber: { type: string, nullable: true }
        title: { type: string, nullable: true }
        applicationDate: { type: string, nullable: true }
        coApplicants: { type: string, nullable: true }
        country: { type: string, nullable: true }
        registrationDate: { type: string, nullable: true }
        applicationNumber: { type: string, nullable: true }
        registrationNumber: { type: string, nullable: true }
        expectedExpirationDate: { type: string, nullable: true }
        source: { type: string, nullable: true }
        businessArea: { type: string, nullable: true }
        technologyArea: { type: string, nullable: true }
        productName: { type: string, nullable: true }
    BusinessChecklistSubmissionRequest:
      type: object
      required: [patentId, responses, finalOpinion]
      properties:
        patentId: { type: string }
        evaluatorName: { type: string, nullable: true }
        evaluatedAt: { type: string, nullable: true }
        responses:
          type: array
          minItems: 1
          items:
            type: object
            required: [itemId, score]
            properties:
              itemId: { type: string }
              score: { type: integer, minimum: 1, maximum: 4 }
              aiSuggestedScore: { type: integer }
              memo: { type: string, nullable: true }
        qualitativeScore: { type: integer, minimum: -5, maximum: 5 }
        qualitativeMemo: { type: string, nullable: true }
        finalOpinion: { $ref: '#/components/schemas/BusinessOpinionDecision' }
        finalReason: { type: string, nullable: true }
        additionalNeeds: { type: string, nullable: true }
    MailingSendRequest:
      type: object
      required: [patentIds]
      properties:
        patentIds:
          type: array
          minItems: 1
          items: { type: string }
    ExecutiveApprovalBulkDecisionRequest:
      type: object
      required: [patentIds, decision]
      properties:
        patentIds:
          type: array
          minItems: 1
          items: { type: string }
        decision: { $ref: '#/components/schemas/ExecutiveApprovalDecision' }
```

## 7. 주요 API 예시

### 7.1 특허 목록 조회

```http
GET /api/v1/patents?page=1&size=20&keyword=AI&reviewWorkflowStatus=MAIL_READY&departmentId=DEPT-RND&sort=title,asc
```

참고:

- `page`는 1부터 시작한다.
- `size`는 최대 20으로 cap 처리한다.
- `sort`는 현재 `annualFeeDueDate`, `title`, `managementNumber` 중심으로 동작한다.

### 7.2 특허 등록

```http
POST /api/v1/patents
Content-Type: application/json

{
  "managementNumber": "SKAX-NEW",
  "title": "신규 테스트 특허",
  "applicationDate": "2022-01-02",
  "coApplicants": "없음",
  "country": "KR",
  "registrationDate": "2024-01-02",
  "applicationNumber": "10-2022-0000004",
  "registrationNumber": "10-2600000",
  "expectedExpirationDate": "2042-01-02",
  "source": "KIPRIS",
  "businessArea": "AI",
  "technologyArea": "문서처리",
  "productName": "PatentFlow"
}
```

응답:

```json
{
  "data": {
    "patentId": "PAT-2026-0124",
    "mode": "CREATED"
  },
  "message": "OK",
  "timestamp": "2026-05-07T10:00:00+09:00"
}
```

### 7.3 사업부 체크리스트 제출

```http
POST /api/v1/patents/PAT-2026-0001/business-submissions
Content-Type: application/json

{
  "patentId": "PAT-2026-0001",
  "evaluatorName": "R&D본부",
  "evaluatedAt": "2026-05-07",
  "responses": [
    {
      "itemId": "TECH_COMPLETENESS",
      "score": 4,
      "aiSuggestedScore": 3,
      "memo": "제품 적용 가능성이 확인됩니다."
    }
  ],
  "qualitativeScore": 2,
  "qualitativeMemo": "사업부 정성 검토",
  "finalOpinion": "MAINTAIN",
  "finalReason": "현재 사업 적용 가능성이 있습니다.",
  "additionalNeeds": "추가 시장 자료"
}
```

처리 규칙:

- path의 `patentId`와 body의 `patentId`가 다르면 `400 INVALID_REQUEST`.
- `responses`는 1개 이상이어야 한다.
- `responses[].score`는 1~4 범위다.
- `qualitativeScore`는 -5~5 범위다.
- 제출 성공 시 특허 상세의 `businessOpinionDecision`과 `businessOpinion`이 갱신되고 workflow는 `BUSINESS_RESPONSE_RECEIVED`가 된다.

### 7.4 메일 발송 처리

```http
POST /api/v1/mailings/send
Content-Type: application/json

{
  "patentIds": ["PAT-2026-0001", "PAT-2026-0003"]
}
```

응답:

```json
{
  "data": {
    "updatedCount": 1,
    "updatedPatentIds": ["PAT-2026-0003"],
    "skippedPatentIds": ["PAT-2026-0001"]
  },
  "message": "OK",
  "timestamp": "2026-05-07T10:00:00+09:00"
}
```

처리 규칙:

- `MAIL_READY`인 특허만 `WAITING_BUSINESS_RESPONSE`로 변경한다.
- 존재하지 않거나 현재 상태가 맞지 않는 특허 ID는 `skippedPatentIds`로 반환한다.

### 7.5 최종 판단 일괄 처리

```http
POST /api/v1/patents/executive-approvals/bulk-decision
Content-Type: application/json

{
  "patentIds": ["PAT-2026-0005"],
  "decision": "APPROVED_ABANDON"
}
```

처리 규칙:

- 허용 workflow: `BUSINESS_RESPONSE_RECEIVED`.
- 그 외 상태는 `409 INVALID_WORKFLOW_STATUS`.
- 현재 구현은 부분 성공을 지원하지 않는다. 하나라도 invalid면 전체 요청이 실패한다.
- legal action result 매핑:
  - `APPROVED_MAINTAIN` -> `MAINTAINED`
  - `APPROVED_ABANDON` -> `ABANDONED`
  - `APPROVED_SELL` -> `SOLD`
  - `REJECTED`, `REQUEST_CHANGES` -> `null`

## 8. 현재 구현상 가정과 한계

- 실제 인증/인가가 없다. 관리자/사업부 사용자 구분은 아직 FE 또는 mock 전제다.
- `PatentFixtureService`가 application memory에 상세 응답과 workflow 상태를 들고 있다.
- 서버 재시작 시 workflow 변경, 사업부 제출 이력, 메일 발송 결과는 영속 보장되지 않는다.
- DB에는 특허 metadata만 저장된다. AI 평가, 사업부 제출, final decision, mailing history는 별도 테이블이 없다.
- KIPRIS는 service key가 없으면 disabled 상태다.
- Google Patents lookup은 공식 API가 아니라 public page fallback 성격이다.
- AI 평가 레포트는 실제 AI serving 연동이 아니라 mock 응답이다.
- `GET /api/v1/patents/{patentId}/history`는 실제 이벤트 저장소가 아니라 고정 mock history를 반환한다.
- `mailings/send`는 실제 SMTP/메일 API를 호출하지 않고 workflow 상태만 변경한다.

## 9. BE 담당자가 이어서 구현해야 할 일

1. 영속 모델 분리
   - `patents` metadata 외에 AI report, summary, business submission, final decision, mailing history, workflow history 테이블 설계가 필요하다.

2. workflow service 정식화
   - 현재 service 내부 조건문으로 관리하는 상태 전이를 별도 도메인/service로 분리해야 한다.
   - 메일 발송, 사업부 제출, 최종 판단의 허용 상태와 전이 결과를 테스트로 고정해야 한다.

3. 실제 사업부 제출 저장
   - `BusinessFixtureService`의 in-memory `submissions`를 repository 기반으로 전환해야 한다.
   - checklist 총점은 사업부 체크리스트 점수만 사용하고 AI 0~100 점수와 섞지 않는 규칙을 유지해야 한다.

4. AI serving 연동
   - 특허 요약 생성, AI 평가 수행, 근거 제공 API 또는 내부 client가 필요하다.
   - AI recommendation은 최종 판단이 아니므로 final decision과 계속 분리해야 한다.

5. 메일링 구현
   - 수신자 mapping, preview, 실제 발송, 발송 이력 저장 API가 필요하다.
   - 현재 `/mailings/send`는 상태 변경용 MVP API다.

6. 인증/인가
   - 관리자와 사업부 사용자 role을 분리해야 한다.
   - 사업부 제출 API는 담당 부서/수신자 기준 권한 확인이 필요하다.

7. OpenAPI 보강
   - 현재 springdoc으로 Swagger UI는 제공되지만 `@Operation`, `@Schema`, 예시 응답 등 상세 annotation은 부족하다.
   - FE 계약이 확정되면 DTO에 schema description/example을 추가하는 것이 좋다.

8. 테스트 확대
   - repository/service 단위 테스트를 추가해야 한다.
   - DB 영속화 전환 후 workflow 상태 전이, validation, not found, conflict 케이스를 유지해야 한다.

## 10. 검증 현황

현재 테스트 파일:

```text
src/test/java/com/syuuk/patentflow/PatentFlowApplicationTests.java
src/test/java/com/syuuk/patentflow/patent/controller/PatentControllerTest.java
```

검증 명령:

```bash
mvn test
```

2026-05-07 문서 작성 후 실행 결과:

- 결과: 실패
- 실패 원인: 테스트 assertion 실패가 아니라 Mockito inline Byte Buddy mock maker 초기화 실패
- 주요 메시지: `Could not initialize inline Byte Buddy mock maker`, `Could not self-attach to current VM using external process`
- 실행 환경: Homebrew OpenJDK 17.0.18, macOS 26.1
- BE 담당자는 JDK attach 설정 또는 Mockito mock maker 설정을 확인한 뒤 재실행해야 한다.

주요 테스트 커버리지:

- 목록 페이징 및 size cap
- 특허 상세의 AI report/final decision 분리
- 이력 조회
- 최종 판단 성공/invalid workflow 실패
- unknown patent 404
- 외부 lookup fixture fallback
- context suggestion
- 사업부 checklist item 조회
- 사업부 제출 성공 및 mismatch patentId 실패
- mailing send 상태 변경 및 skipped 처리
- 특허 등록/수정

## 11. BE 담당자에게 꼭 전달할 주의 사항

- FE가 기대하는 enum 문자열을 변경하면 연동이 깨진다.
- display label은 FE에서 처리하고, BE는 enum value를 내려주는 현재 방식을 유지하는 편이 좋다.
- `AI 특허 평가 레포트`, `최종 판단`, `사업부 의견`, `평가 근거`는 응답과 DB 모델에서 계속 분리해야 한다.
- `BUSINESS_ALIGNMENT`는 사업 연계성 평가축으로 사용한다.
- business opinion은 `MAINTAIN`, `ABANDON` 두 값만 사용한다.
- AI recommendation label 계열은 `MAINTAIN`, `REVIEW_AGAIN`, `ABANDON`, `SALES_CANDIDATE`, `HOLD` enum을 쓰되 화면 label은 FE에서 `유지 권고`, `추가 정보 필요`, `포기 검토`로 매핑한다.
- checklist 총점과 AI 0~100 점수는 서로 다른 출처이므로 하나의 total로 섞지 않는다.
