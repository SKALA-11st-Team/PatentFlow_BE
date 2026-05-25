# PatentFlow API Integration Status

Last checked: 2026-05-25

## Summary

- Base path: `/api/v1`
- Verification mode: direct HTTP requests against a PostgreSQL-backed local BE. No FE mock was used.
- Response envelope: `ApiResponse<T>` uses `data`, `message`, `timestamp`; paged APIs add `page`.
- Auth: JWT bearer token and HttpOnly cookie refresh were both verified.
- Overall status: OK. 64 API requests were executed, then 2 initially failing cases were fixed/rechecked successfully.
- Removed contract: sales states are not part of the deployment API. `SOLD` and `SALES_CANDIDATE` are absent from source enums and OpenAPI schemas; `SOLD` request bodies now return `400 INVALID_REQUEST`.
- Removed contract: lifecycle/economic valuation report content is not merged into AI report output.

## API Matrix

| Area | Method | Path | Auth | DB request result |
|---|---:|---|---|---|
| Auth | POST | `/auth/login` | Public | OK |
| Auth | POST | `/auth/refresh` | Public cookie refresh | OK |
| Auth | GET | `/auth/me` | Authenticated | OK |
| Auth | PATCH | `/auth/me` | Authenticated | OK |
| Auth | POST | `/auth/logout` | Public, revokes if token exists | OK |
| Patents | GET | `/patents` | ADMIN | OK |
| Patents | GET | `/patents/review-targets` | ADMIN | OK |
| Patents | POST | `/patents` | ADMIN | OK |
| Patents | GET | `/patents/external-lookup` | ADMIN | OK |
| Patents | POST | `/patents/context-suggestions` | ADMIN | OK |
| Patents | GET | `/patents/{patentId}` | ADMIN | OK |
| Patents | PUT | `/patents/{patentId}` | ADMIN | OK |
| Patents | GET | `/patents/{patentId}/history` | ADMIN | OK |
| Patents | POST | `/patents/{patentId}/final-decision` | ADMIN | OK; only `MAINTAINED`/`ABANDONED` accepted |
| Patents | PATCH | `/patents/{patentId}/final-decision` | ADMIN | OK |
| Patents | PATCH | `/patents/{patentId}/department` | ADMIN | OK |
| Patents | POST | `/patents/{patentId}/request-ai-report` | ADMIN | OK when status is `REVIEW_QUARTER_STARTED`; invalid workflow returns 409 |
| Patents | POST | `/patents/batch/mark-mail-ready` | ADMIN | OK |
| Business | GET | `/business/checklist-items` | Authenticated | OK |
| Business | GET | `/business/dashboard/summary` | BUSINESS | OK |
| Business | GET | `/business/review-requests` | BUSINESS | OK |
| Business | GET | `/business/patents` | BUSINESS, own department | OK |
| Business | GET | `/business/patents/{patentId}` | BUSINESS, own department | OK |
| Business | GET | `/patents/{patentId}/business-submissions` | ADMIN or assigned BUSINESS | OK |
| Business | POST | `/patents/{patentId}/business-submissions` | Assigned BUSINESS | OK |
| Legal | GET | `/legal/dashboard/summary` | ADMIN | OK |
| Mailing | GET | `/mailings/department-recipient-mappings` | ADMIN | OK |
| Mailing | PUT | `/mailings/department-recipient-mappings/{departmentId}` | ADMIN | OK |
| Mailing | POST | `/mailings/send` | ADMIN | OK; records without SMTP when Gmail settings are blank |
| Mailing | GET | `/mailings/history` | ADMIN | OK |
| Settings | GET | `/admin/settings/mail` | ADMIN | OK |
| Settings | PUT | `/admin/settings/mail` | ADMIN | OK |
| Settings | GET | `/settings/review-quarters` | ADMIN | OK |
| Settings | GET | `/settings/review-quarters/active` | ADMIN or BUSINESS | OK |
| Settings | PUT | `/settings/review-quarters/{quarterKey}` | ADMIN | OK |
| Settings | GET | `/settings/review-schedule` | ADMIN | OK |
| Settings | PATCH | `/settings/review-schedule` | ADMIN | OK |
| Settings | POST | `/settings/review-quarters/{quarterKey}/activate` | ADMIN | OK |
| Settings | POST | `/settings/review-quarters/{quarterKey}/end` | ADMIN | OK |
| Settings | GET | `/settings/country-extensions` | ADMIN | OK |
| Settings | PUT | `/settings/country-extensions/{country}` | ADMIN | OK |
| Settings | GET | `/settings/classifications` | ADMIN | OK |
| Settings | POST | `/settings/classifications/{type}` | ADMIN | OK |
| Settings | PUT | `/settings/classifications/{type}/{value}` | ADMIN | OK |
| Settings | DELETE | `/settings/classifications/{type}/{value}` | ADMIN | OK |
| Annual fees | GET | `/annual-fees/schedule` | ADMIN | OK; application-date basis |
| Annual fees | PATCH | `/annual-fees/schedule/{patentId}` | ADMIN | OK |
| Admin users | GET | `/admin/users` | ADMIN | OK |
| Admin users | POST | `/admin/users` | ADMIN | OK |
| Admin users | PUT | `/admin/users/{userId}` | ADMIN | OK |
| Admin users | DELETE | `/admin/users/{userId}` | ADMIN | OK |
| Admin users | POST | `/admin/users/{userId}/reset-password` | ADMIN | OK |
| Departments | GET | `/departments` | ADMIN | OK |
| Admin departments | GET | `/admin/departments` | ADMIN | OK |
| Admin departments | POST | `/admin/departments` | ADMIN | OK |
| Admin departments | PUT | `/admin/departments/{departmentId}` | ADMIN | OK |
| Admin departments | DELETE | `/admin/departments/{departmentId}` | ADMIN | OK |
| Notifications | GET | `/notifications?role={role}` | Authenticated, current role only | OK |
| Notifications | PATCH | `/notifications/{notificationId}/read-state` | Authenticated, own/common only | OK |

## Verification

- DB API run: local PostgreSQL on `jdbc:postgresql://localhost:5432/patentflow?currentSchema=patentflow`.
- BE: `mvn test` -> 34 tests passed.
- FE: `npm test -- --run` -> 14 tests passed.
- FE: `npm run build` -> passed, Vite chunk-size warning only.
- FE: `npm run lint` -> passed.
