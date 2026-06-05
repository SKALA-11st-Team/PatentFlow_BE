# PatentFlow BE

PatentFlow backend MVP scaffold for FE API integration.

## Run

```bash
docker compose up --build
```

Local verification:

```bash
mvn test
```

API base URL:

```text
http://localhost:8080/api/v1
```

Agent URL is configured with `PATENTFLOW_AGENT_URL`. In Docker Compose it points to:

```text
http://team11-patentflow-agent-svc:8000
```

JWT authentication is enabled for `/api/**` endpoints. Public endpoints are:

- `POST /api/v1/auth/login`
- `GET /actuator/health`
- Swagger UI and OpenAPI docs

Login ID is the user **email** (`email` field, validated as an email address). There is no username-based login.

Demo/seed accounts are created by `src/main/resources/db/seed/core_review_workflow_seed.sql`
(loaded by `LocalDemoSeedRunner` under the `local`/`demo` profiles on PostgreSQL):

```text
admin@syuuk.test            ADMIN     (특허관리자)
rnd.manager@syuuk.test      BUSINESS  (R&D 담당자)
platform.manager@syuuk.test BUSINESS  (플랫폼 담당자)
esg.manager@syuuk.test      BUSINESS  (ESG 담당자)
ict.manager@syuuk.test      BUSINESS  (ICT 담당자)
mfg.manager@syuuk.test      BUSINESS  (제조 담당자)
biz.manager@syuuk.test      BUSINESS  (사업기획 담당자)
```

Passwords are the bcrypt values stored in that seed file. To use a known password, update the
target `users` row (e.g. `USER-admin`) with a freshly generated bcrypt hash. Note: the seed only
re-runs at startup when baseline rows are missing (`LocalDemoSeedRunner.needsCoreWorkflowSeed`),
so editing the seed file does not change an already-populated database.

For non-`local`/`demo` profiles, the first administrator is created from environment variables
(`BootstrapAdminInitializer` runs only when the profile is not `local`/`demo` and the email does
not already exist; it is disabled under `demo`):

```bash
PATENTFLOW_BOOTSTRAP_ADMIN_USERNAME=admin@example.com   # used as the login email
PATENTFLOW_BOOTSTRAP_ADMIN_PASSWORD=change-this-initial-admin-password
PATENTFLOW_BOOTSTRAP_ADMIN_DISPLAY_NAME=특허관리자
```

Login example:

```bash
curl -X POST https://api.patentflow.live/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@syuuk.test","password":"<password>"}'
```

Use the returned token as a Bearer token:

```bash
curl http://localhost:8080/api/v1/patents \
  -H "Authorization: Bearer <accessToken>"
```

Configure the JWT secret in runtime environments:

```bash
PATENTFLOW_JWT_SECRET=change-this-secret
PATENTFLOW_JWT_EXPIRATION_SECONDS=3600
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

## MVP APIs

- `POST /api/v1/auth/login` - JWT login
- `GET /api/v1/auth/me` - Current authenticated user
- `GET /api/v1/patents` - FR-001, FR-002
- `GET /api/v1/patents/{patentId}` - FR-005, FR-006, FR-007, FR-008, FR-011, FR-012
- `GET /api/v1/patents/{patentId}/history` - FR-013

The current implementation loads official demo patent metadata from `docs/skax_patents_list.md` and adds mock evaluation/workflow data around it.
The same SK AX patent metadata is also available as an idempotent SQL seed in `src/main/resources/db/seed/skax_patents.sql`.

## Domain Decisions

- The workflow does not include a separate approval step. After `BUSINESS_RESPONSE_RECEIVED`, the administrator/legal user records the final decision and legal action result directly.
- Do not add separate approval statuses or decision APIs/types unless the team explicitly reverses this decision.
- `patent_review_history` is the source of truth for the current quarter review state, including workflow status, AI recommendation/report snapshot, patent summary snapshot, business opinion snapshot, and final legal decision.
- `business_submissions` stores the business user's submitted checklist/opinion and keeps AI recommendation/report values only as submission-time snapshots. Do not read it as the current AI recommendation source.

## DB Seed

- `src/main/resources/db/seed/skax_patents.sql` seeds 185 SK AX patent rows from `docs/skax_patent_list.csv`.
- `src/main/resources/db/seed/core_review_workflow_seed.sql` seeds departments, users, country settings, 2026 quarters, and initial 2026-Q2 review history.
- `src/main/resources/db/seed/demo_workflow_seed.sql` seeds presentation-friendly workflow states, mailing history, business submissions, and the `business / business1234` demo account.
- In `local` and `demo` profiles, the application runs an idempotent seed runner after Hibernate creates/updates tables. Existing patent data is not duplicated, and demo workflow rows are skipped once present.
- `docs/db_seed_and_status_plan.md` lists the remaining seed data and DB-side status/date update functions needed before production-like operation.

## External Patent Lookup

`GET /api/v1/patents/external-lookup` searches by `applicationNumber` first and tries sources in `sourcePriority` order.

```text
applicationNumber=10-2024-0115774
sourcePriority=KIPRIS,GOOGLE_PATENTS
```

KIPRIS lookup runs when one or more KIPRISPlus service keys are configured. If a key reaches its monthly request limit, the backend skips that key for the current month and tries the next configured key. KIPRISPlus monthly request limits reset on the 1st day of each month.

```bash
PATENTFLOW_KIPRIS_SERVICE_KEY=...
# or comma-separated multiple keys
PATENTFLOW_KIPRIS_SERVICE_KEYS=key1,key2,key3
```

KIPRISPlus URLs follow the OpenAPI operation pattern `base-url + service-path + operationName`.

```bash
PATENTFLOW_KIPRIS_BASE_URL=http://plus.kipris.or.kr
PATENTFLOW_KIPRIS_SERVICE_PATH=/kipo-api/kipi/patUtiModInfoSearchSevice
PATENTFLOW_KIPRIS_BIBLIOGRAPHY_OPERATION=getBibliographyDetailInfoSearch
# Optional wrapper-only fallback operation. Leave blank for direct KIPRISPlus source calls.
PATENTFLOW_KIPRIS_APPLICATION_SEARCH_OPERATION=
```

Google Patents is implemented as a public page fallback, not an official JSON API. If external lookup fails or has missing fields, the API falls back to `docs/skax_patents_list.md` metadata where available.

## AWS/EKS Note

When updating kubeconfig, use the same profile name configured by `aws configure`.

```bash
aws eks update-kubeconfig --region ap-northeast-2 --name skala-2025 --profile skala-student
```
