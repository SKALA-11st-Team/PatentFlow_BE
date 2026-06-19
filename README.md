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

## 필요 API

엔드포인트 전체와 요청/응답 스키마는 Swagger UI가 단일 출처입니다. 아래는 도메인별 요약입니다.

| 도메인 | 베이스 경로 | 주요 기능 | 관련 FR |
|---|---|---|---|
| 인증 | `/api/v1/auth` | 로그인·토큰 갱신·내 정보·비밀번호 변경·로그아웃 | FR-COM |
| 특허 | `/api/v1/patents` | 목록·검색·필터·상세·등록/수정·이력·최종 판단·AI 레포트 요청/상태·외부 조회·PDF | FR-LEGAL |
| 연차료 일정 | `/api/v1/annual-fees/schedule` | 일정 조회·개별 수정·재계산 | FR-LEGAL |
| 법무 | `/api/v1/legal` | 감사 로그·대시보드 요약·기술분야 분포 | FR-LEGAL |
| 사업부 | `/api/v1/business` | 대시보드·검토 요청·담당 특허 상세·의견(체크리스트) 제출 | FR-BUS |
| 메일링 | `/api/v1/mailings` | 수신자 매핑·발송·발송 이력 | FR-LEGAL |
| 알림 | `/api/v1/notifications` | 목록·미읽음 수·읽음 처리 | FR-COM |
| 설정 | `/api/v1/settings` | 평가 기준·체크리스트·검토 분기·회신 기한·연차료 규칙·국가 설정 | FR-LEGAL |
| 관리 | `/api/v1/admin/*` | 사용자·부서·초대 관리, 메일 OAuth2 연동 | FR-LEGAL |

## 도메인 결정 (드리프트 금지)

- 워크플로우에 **별도 결재 단계가 없습니다.** `BUSINESS_RESPONSE_RECEIVED` 이후 관리자/법무가 최종 결정과 법무 처리 결과를 직접 기록합니다. 별도 결재 상태나 결재용 API/타입을 추가하지 않습니다.
- `patent_review_history`가 **현재 분기 검토 상태의 단일 출처**입니다(워크플로우 상태, AI 권고/레포트 스냅샷, 특허 요약 스냅샷, 사업부 의견 스냅샷, 최종 법무 결정 포함).
- `business_submissions`는 사업부가 제출한 체크리스트/의견을 저장하며, AI 권고/레포트 값은 **제출 시점 스냅샷**으로만 보관합니다. 현재 AI 권고의 출처로 읽지 않습니다.
- AI 평가 축은 **권리성·기술성·시장성·사업 연계성** 4개뿐입니다.
