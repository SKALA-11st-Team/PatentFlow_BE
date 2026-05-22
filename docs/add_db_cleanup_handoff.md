# add-db-cleanup Backend Handoff

이 문서는 `add-db-cleanup` 브랜치에서 `feat/add-db` 변경분을 정리하며 결정한 주요 내용과 다음 세션에서 이어볼 가능성이 높은 항목을 기록한다.

## 현재 브랜치 상태

- 대상 브랜치: `add-db-cleanup`
- 주요 목적: `feat/add-db` 변경분을 기능 단위로 선별하고, DB/API/Auth/Seed를 main 병합 가능한 형태로 정리
- FE도 같은 이름의 `add-db-cleanup` 브랜치에서 연동 작업 중

## 주요 커밋

- `feat: patentflow 스키마와 관리자 수정 API 보강`
  - DB 기본 스키마를 `public`에서 `patentflow`로 변경
  - Docker Postgres 초기화 SQL 추가
  - 관리자 사용자/부서 수정 API 추가
- `docs: 최신 FR UI 계약 반영`
- `docs: BE FR UI 주석 최신화`
- 이전 cleanup 커밋에는 임원 actor 제거, DB source of truth 전환, seed/local-demo 정리, auth 고도화, AI report 저장 구조 정리가 포함되어 있다.

## DB 스키마 결정

DB 테이블은 더 이상 `public` 스키마 기준으로 보지 않고 `patentflow` 스키마 기준으로 사용한다.

적용 사항:

- `SPRING_DATASOURCE_URL` 기본값에 `?currentSchema=patentflow` 추가
- `spring.jpa.properties.hibernate.default_schema=${PATENTFLOW_DB_SCHEMA:patentflow}` 추가
- `hibernate.hbm2ddl.create_namespaces=true` 추가
- Docker Postgres 초기화 스크립트 추가:
  - `docker/postgres/init/01-create-patentflow-schema.sql`

주의:

- 새 Docker volume에서는 `patentflow` 스키마가 자동 생성된다.
- 기존 volume에 이미 `public` 스키마로 들어간 데이터는 자동 이관하지 않는다.
- 기존 데이터를 보존해야 하면 별도 migration SQL이 필요하다.

예상 migration 방향:

```sql
CREATE SCHEMA IF NOT EXISTS patentflow AUTHORIZATION patentflow;
-- 필요 테이블을 확인한 뒤 public.<table> 에서 patentflow.<table> 로 이관
-- ALTER TABLE public.patents SET SCHEMA patentflow; 방식은 충돌 여부를 먼저 확인해야 한다.
```

## Seed와 Local/Demo 데이터

현재 seed 관련 파일:

- `src/main/resources/db/seed/core_review_workflow_seed.sql`
- `src/main/resources/db/seed/demo_workflow_seed.sql`
- `src/main/resources/db/seed/skax_patents.sql`
- `docs/skax_patent_list.csv`
- `docs/skax_patents_list.md`

정리된 방향:

- SK AX 특허 메타데이터는 실제 보유 특허 목록을 기준으로 seed한다.
- demo workflow, mailing history, business submissions 등 화면 확인용 데이터는 local/demo 용도로만 유지한다.
- SQL seed는 idempotent하게 작성해서 재시작 시 중복 삽입되지 않도록 한다.
- bootstrap admin은 `.env`의 `PATENTFLOW_BOOTSTRAP_ADMIN_USERNAME/PASSWORD/DISPLAY_NAME` 기준으로 생성한다.

다음 점검:

- `patentflow` 스키마 전환 후 기존 local DB volume을 그대로 쓸 경우 seed가 새 스키마에 적용되었는지 확인해야 한다.
- 데모에서 필요한 상태별 특허 수가 충분한지 수동 QA 화면 기준으로 다시 확인한다.

## Auth 구현 수준

현재 구현된 것:

- username/password 로그인
- JWT access token
- refresh token/session 저장소
- httpOnly cookie 기반 refresh
- 로그인 실패 제한
- role 기반 접근 제어
- bootstrap admin 생성

현재 미구현:

- Google OAuth 로그인
- Gmail 발송용 Google OAuth 계정 연결
- OAuth token 저장/갱신/폐기

Google 관련 코드 중 현재 존재하는 것은 Google OAuth가 아니라 Google Patents 외부 조회 클라이언트다.

## 관리자 사용자/부서 API 보강

FE가 이미 호출하던 API가 BE에 없어 추가했다.

추가된 API:

- `PUT /api/v1/admin/users/{userId}`
- `PUT /api/v1/admin/departments/{departmentId}`

사용자 수정은 username 중복을 검사한다.

부서 수정은 부서명을 변경하고 `PatentReviewService.refreshDepartmentCache()`를 호출한다.

## AI Report 저장 구조

AI 평가 레포트 원문 markdown은 DB와 로컬 파일 시스템에 병렬로 저장하는 방향으로 정리했다.

현재 방향:

- DB: 조회/화면 표시/이력 추적용 텍스트 및 메타데이터 저장
- 로컬 파일 시스템: 원문 markdown 파일 저장
- Docker compose에서는 `ai-report-storage` volume을 `/app/storage/ai-reports`에 mount

MinIO는 현재 필수 구성에서 제외했다.

## Workflow Status 결정

`REPORT_GENERATED`는 더 이상 현재 workflow 상태로 사용하지 않는다.

현재 FE/BE가 맞춰야 하는 상태:

```text
NOT_IN_REVIEW_QUARTER
REVIEW_QUARTER_STARTED
MAIL_READY
WAITING_BUSINESS_RESPONSE
BUSINESS_RESPONSE_RECEIVED
LEGAL_ACTION_RECORDED
```

AI 평가 레포트 생성 완료 후에는 `REPORT_GENERATED`를 거치지 않고 바로 `MAIL_READY`가 된다.

## Docker Compose 결정

Docker compose는 앞으로 backend repo에서 관리한다.

현재 compose 구성:

- `patentflow-api`
- `patentflow-agent`
- `postgres`

FE는 Docker로 띄우지 않고 로컬 dev server로 실행한다.

## 검증 기록

마지막 확인 기준:

- BE `mvn test` 성공
- Docker compose config 검증 성공
- FE build는 FE repo에서 별도 성공 확인

수동 QA는 아직 사용자가 직접 진행할 예정이며, 실제 브라우저 동작 결과에 따라 추가 수정 가능성이 있다.

## 다음 세션 우선순위

1. 기존 local Docker volume을 유지할지 삭제/재생성할지 결정
2. `patentflow` 스키마에 실제 테이블/seed가 들어갔는지 DB에서 확인
3. 관리자/사업부 로그인 후 권한별 API 401/403 확인
4. 특허 목록 185건과 상태별 목록/수정 화면 수량 차이 확인
5. AI report 생성 후 `MAIL_READY` 전환 및 markdown 저장 확인
6. 메일링 발송 기록, business submission, final decision 이력 갱신 확인
7. Google OAuth가 필요한 경우 로그인 OAuth인지 Gmail 발송 OAuth인지 먼저 확정
