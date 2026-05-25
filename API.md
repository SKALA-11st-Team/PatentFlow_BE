# PatentFlow API Integration Status

Last checked: 2026-05-25

## Summary

- Base path: `/api/v1`
- FE base handling: `VITE_API_BASE_URL` is normalized to include `/api/v1`.
- Response envelope: `ApiResponse<T>` uses `data`, `message`, `timestamp`; paged APIs add `page`.
- Auth: JWT bearer token and HttpOnly cookies are supported.
- Overall status: FE and BE API paths are aligned. The main fixes in this pass were enum parity, annual-fee base-date parity, lifecycle persistence, notification role authorization, and UI copy cleanup.

## API Matrix

| Area | Method | Path | FE status | Auth status | Result |
|---|---:|---|---|---|---|
| Auth | POST | `/auth/login` | Connected | Public | OK |
| Auth | POST | `/auth/refresh` | Connected | Public cookie refresh | OK |
| Auth | GET | `/auth/me` | Connected | Authenticated | OK |
| Auth | PATCH | `/auth/me` | Connected | Authenticated | OK |
| Auth | POST | `/auth/logout` | Connected | Public, revokes if token exists | OK |
| Patents | GET | `/patents` | Connected | ADMIN | OK |
| Patents | GET | `/patents/review-targets` | BE available, FE currently uses `/patents` filters | ADMIN | OK |
| Patents | POST | `/patents` | Connected | ADMIN | OK |
| Patents | GET | `/patents/external-lookup` | Connected | ADMIN | OK |
| Patents | POST | `/patents/context-suggestions` | Connected | ADMIN | OK |
| Patents | GET | `/patents/{patentId}` | Connected | ADMIN | OK |
| Patents | PUT | `/patents/{patentId}` | Connected | ADMIN | OK |
| Patents | GET | `/patents/{patentId}/history` | Connected | ADMIN | OK |
| Patents | POST | `/patents/{patentId}/final-decision` | Connected | ADMIN | Fixed lifecycle persistence |
| Patents | PATCH | `/patents/{patentId}/final-decision` | BE available | ADMIN | OK |
| Patents | PATCH | `/patents/{patentId}/department` | Connected | ADMIN | OK |
| Patents | POST | `/patents/{patentId}/request-ai-report` | Connected | ADMIN | OK |
| Patents | POST | `/patents/batch/mark-mail-ready` | Connected | ADMIN | OK |
| Business | GET | `/business/checklist-items` | Connected | ADMIN or BUSINESS | OK |
| Business | GET | `/business/dashboard/summary` | Connected | BUSINESS | OK |
| Business | GET | `/business/review-requests` | BE available, FE currently uses `/business/patents` | BUSINESS | OK |
| Business | GET | `/business/patents` | Connected | BUSINESS, own department | OK |
| Business | GET | `/business/patents/{patentId}` | Connected | BUSINESS, own department | OK |
| Business | GET | `/patents/{patentId}/business-submissions` | Connected | ADMIN or assigned BUSINESS | OK |
| Business | POST | `/patents/{patentId}/business-submissions` | Connected | assigned BUSINESS | OK |
| Legal | GET | `/legal/dashboard/summary` | Connected | ADMIN | OK |
| Mailing | GET | `/mailings/department-recipient-mappings` | Connected | ADMIN | OK |
| Mailing | PUT | `/mailings/department-recipient-mappings/{departmentId}` | Connected | ADMIN | OK |
| Mailing | POST | `/mailings/send` | Connected | ADMIN | OK |
| Mailing | GET | `/mailings/history` | Connected | ADMIN | OK |
| Settings | GET | `/admin/settings/mail` | Connected | ADMIN | OK |
| Settings | PUT | `/admin/settings/mail` | Connected | ADMIN | OK |
| Settings | GET | `/settings/review-quarters` | Connected | ADMIN | OK |
| Settings | GET | `/settings/review-quarters/active` | Connected | ADMIN or BUSINESS | OK |
| Settings | PUT | `/settings/review-quarters/{quarterKey}` | Connected | ADMIN | OK |
| Settings | GET | `/settings/review-schedule` | BE available | ADMIN | OK |
| Settings | PATCH | `/settings/review-schedule` | Connected | ADMIN | OK |
| Settings | POST | `/settings/review-quarters/{quarterKey}/activate` | Connected | ADMIN | OK |
| Settings | POST | `/settings/review-quarters/{quarterKey}/end` | Connected | ADMIN | OK |
| Settings | GET | `/settings/country-extensions` | Connected | ADMIN | OK |
| Settings | PUT | `/settings/country-extensions/{country}` | Connected | ADMIN | OK |
| Settings | GET | `/settings/classifications` | Connected | ADMIN | OK |
| Settings | POST | `/settings/classifications/{type}` | Connected | ADMIN | OK |
| Settings | PUT | `/settings/classifications/{type}/{value}` | Connected | ADMIN | OK |
| Settings | DELETE | `/settings/classifications/{type}/{value}` | Connected | ADMIN | OK |
| Annual fees | GET | `/annual-fees/schedule` | Connected | ADMIN | Fixed to application-date base |
| Annual fees | PATCH | `/annual-fees/schedule/{patentId}` | Connected | ADMIN | OK |
| Admin users | GET | `/admin/users` | Connected | ADMIN | OK |
| Admin users | POST | `/admin/users` | Connected | ADMIN | OK |
| Admin users | PUT | `/admin/users/{userId}` | Connected | ADMIN | OK |
| Admin users | DELETE | `/admin/users/{userId}` | Connected | ADMIN | OK |
| Admin users | POST | `/admin/users/{userId}/reset-password` | Connected | ADMIN | OK |
| Departments | GET | `/departments` | Connected | ADMIN | OK |
| Admin departments | GET | `/admin/departments` | Connected | ADMIN | OK |
| Admin departments | POST | `/admin/departments` | Connected | ADMIN | OK |
| Admin departments | PUT | `/admin/departments/{departmentId}` | Connected | ADMIN | OK |
| Admin departments | DELETE | `/admin/departments/{departmentId}` | Connected | ADMIN | OK |
| Notifications | GET | `/notifications?role={role}` | Connected | Authenticated, current role only | Fixed authorization |
| Notifications | PATCH | `/notifications/{notificationId}/read-state` | Connected | Authenticated, own/common only | Fixed authorization |

## Verified Commands

- BE: `mvn test` -> 34 tests passed.
- FE: `npm test` -> 14 tests passed.
- FE: `npm run build` -> passed, Vite chunk-size warning only.
- FE: `npm run lint` -> passed.
