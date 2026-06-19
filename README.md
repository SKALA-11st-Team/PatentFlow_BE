# PatentFlow Backend

PatentFlow는 SYUUK(11팀)의 사내 **특허 관리 AI 워크플로우** 시스템이고, 백엔드는 그 **권위 있는 애플리케이션 API**입니다. 연차료 납부 시점 전후로 보유 특허의 유지·포기 검토를 운영하며, AI 평가 레포트는 권고일 뿐 최종 결정은 사람이 기록하는 human-in-the-loop 구조를 API로 보장합니다.

## 시스템 속 위치

- **FE → BE**: 프론트엔드가 호출하는 단일 권위 API입니다.
- **BE → Agent**: AI 평가 레포트는 BE가 Agent를 HTTP로 호출해 생성합니다. FE는 정상 흐름에서 Agent를 직접 호출하지 않습니다.
- Agent URL은 `PATENTFLOW_AGENT_URL`로 설정합니다(Docker Compose 기본값: `http://team11-patentflow-agent-svc:8000`).

## 빠른 시작

```bash
docker compose up --build      # 로컬 스택 기동
mvn test                       # 로컬 검증
```

| 항목 | 주소 |
|---|---|
| API base | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Health | `http://localhost:8080/actuator/health` |

## 인증 & 계정

- `/api/**` 엔드포인트는 JWT 인증을 사용합니다. 공개 엔드포인트는 `POST /api/v1/auth/login`, `GET /actuator/health`, Swagger/OpenAPI 문서입니다.
- 로그인 ID는 **이메일**입니다(`email` 필드). username 로그인은 없습니다.

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@syuuk.test","password":"<password>"}'
# 반환된 accessToken을 Bearer 토큰으로 사용
curl http://localhost:8080/api/v1/patents -H "Authorization: Bearer <accessToken>"
```

- 데모/시드 계정은 `local`/`demo` 프로파일에서 생성됩니다: `admin@syuuk.test`(ADMIN), 그리고 부서별 BUSINESS 계정(`rnd.manager@`, `ict.manager@`, `esg.manager@syuuk.test` 등).
- 관리자 계정은 **모든 프로파일**에서 환경변수로 upsert됩니다. EKS는 `PATENTFLOW_BOOTSTRAP_ADMIN_*` 시크릿으로 구동합니다.

```bash
PATENTFLOW_BOOTSTRAP_ADMIN_USERNAME=admin@example.com   # 로그인 이메일로 사용
PATENTFLOW_BOOTSTRAP_ADMIN_PASSWORD=change-this-initial-admin-password
PATENTFLOW_JWT_SECRET=change-this-secret
PATENTFLOW_JWT_EXPIRATION_SECONDS=3600
```

## 필요 API (외부 API 키)

아래 외부 서비스는 키/자격증명이 설정돼야 동작합니다. 미설정 시 해당 기능은 폴백되거나 건너뜁니다. 내부 엔드포인트 전체와 스키마는 Swagger UI가 단일 출처입니다.

| 외부 API | 용도 | 환경변수 | 키 필요 |
|---|---|---|---|
| KIPRIS Plus | 특허 서지·공개전문 PDF 조회 | `PATENTFLOW_KIPRIS_SERVICE_KEY` (복수는 `PATENTFLOW_KIPRIS_SERVICE_KEYS`) | ✅ |
| Google OAuth2 | 메일 발송 계정 연동(Google 계정 연동) | `GOOGLE_OAUTH2_CLIENT_ID`, `GOOGLE_OAUTH2_CLIENT_SECRET` | ✅ |
| AWS S3 | 특허 PDF 캐시·presigned 다운로드 링크 | `PATENTFLOW_PDF_S3_BUCKET` (+ AWS 자격증명) | ✅ |
| Google Patents | 외부 조회 폴백(공개 페이지) | — | ❌ 키 불필요 |
