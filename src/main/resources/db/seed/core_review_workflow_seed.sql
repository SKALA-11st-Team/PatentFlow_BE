-- PatentFlow core reference and review workflow seed
-- Run after skax_patents.sql.

INSERT INTO departments (department_id, department_name, updated_at) VALUES
    ('DEPT-RND', 'R&D본부', CURRENT_DATE),
    ('DEPT-PLATFORM', '플랫폼사업부', CURRENT_DATE),
    ('DEPT-ESG', 'ESG사업부', CURRENT_DATE),
    ('DEPT-ICT', 'ICT사업부', CURRENT_DATE),
    ('DEPT-MFG', '제조사업부', CURRENT_DATE),
    ('DEPT-BIZ', '사업기획팀', CURRENT_DATE)
ON CONFLICT (department_id) DO UPDATE SET
    department_name = EXCLUDED.department_name,
    updated_at = EXCLUDED.updated_at;

-- status: 초대 온보딩 상태(ACTIVE | PENDING | INACTIVE). 초대가 PENDING/REVOKED인 계정은 일관되게 맞춘다.
--   USER-esg-manager → 초대 미수락(PENDING), USER-rnd-manager → 초대 회수(INACTIVE). 그 외 ACTIVE.
INSERT INTO users (id, email, password, role, department_id, username, status, created_at) VALUES
    ('USER-admin', 'admin@syuuk.test', '$2a$10$./V2Hi1S3qoucv7eCn3UPevz.heOde1x3SehhcAJCss8OXimprQFC', 'ADMIN', NULL, '특허관리자', 'ACTIVE', CURRENT_TIMESTAMP),
    ('USER-rnd-manager', 'rnd.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-RND', 'R&D 담당자', 'INACTIVE', CURRENT_TIMESTAMP),
    ('USER-platform-manager', 'platform.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-PLATFORM', '플랫폼 담당자', 'ACTIVE', CURRENT_TIMESTAMP),
    ('USER-esg-manager', 'esg.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-ESG', 'ESG 담당자', 'PENDING', CURRENT_TIMESTAMP),
    ('USER-ict-manager', 'ict.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-ICT', 'ICT 담당자', 'ACTIVE', CURRENT_TIMESTAMP),
    ('USER-mfg-manager', 'mfg.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-MFG', '제조 담당자', 'ACTIVE', CURRENT_TIMESTAMP),
    ('USER-biz-manager', 'biz.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-BIZ', '사업기획 담당자', 'ACTIVE', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    password = EXCLUDED.password,
    role = EXCLUDED.role,
    department_id = EXCLUDED.department_id,
    username = EXCLUDED.username,
    status = EXCLUDED.status;

INSERT INTO system_settings (setting_key, setting_value, updated_at) VALUES
    ('country.extension.KR', '12', CURRENT_TIMESTAMP),
    ('country.extension.JP', '12', CURRENT_TIMESTAMP),
    ('country.extension.CN', '12', CURRENT_TIMESTAMP),
    ('country.extension.US', '12', CURRENT_TIMESTAMP),
    ('country.extension.TW', '12', CURRENT_TIMESTAMP)
ON CONFLICT (setting_key) DO UPDATE SET
    setting_value = EXCLUDED.setting_value,
    updated_at = EXCLUDED.updated_at;


-- Q1은 납부 기간(3월)이 지났으므로 ended=true.
-- Q2 이후는 미활성 상태로 둔다. 검토 시작일이 지나면 백엔드 스케줄러가 활성화하며 검토 이력을 생성한다.
-- submission_deadline = 활성화일(검토 시작일) + 회신기한(기본 1개월)
-- mail_lead_months_snapshot = 활성화 시점의 메일 발송 기준 개월 수 스냅샷 (기본 3)
INSERT INTO quarter_settings (
    quarter_key,
    setting_year,
    quarter_number,
    start_date,
    end_date,
    activated,
    activated_at,
    ended,
    ended_at,
    submission_deadline,
    mail_lead_months_snapshot
) VALUES
    ('2026-Q1', 2026, 1, DATE '2026-01-01', DATE '2026-03-31', true, TIMESTAMP WITH TIME ZONE '2026-01-01 09:00:00+09', true, TIMESTAMP WITH TIME ZONE '2026-03-31 18:00:00+09', DATE '2026-02-01', 3),
    ('2026-Q2', 2026, 2, DATE '2026-04-01', DATE '2026-06-30', false, NULL, false, NULL, NULL, NULL),
    ('2026-Q3', 2026, 3, DATE '2026-07-01', DATE '2026-09-30', false, NULL, false, NULL, NULL, NULL),
    ('2026-Q4', 2026, 4, DATE '2026-10-01', DATE '2026-12-31', false, NULL, false, NULL, NULL, NULL)
ON CONFLICT (quarter_key) DO UPDATE SET
    setting_year = EXCLUDED.setting_year,
    quarter_number = EXCLUDED.quarter_number,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    activated = EXCLUDED.activated,
    activated_at = EXCLUDED.activated_at,
    ended = EXCLUDED.ended,
    ended_at = EXCLUDED.ended_at,
    submission_deadline = EXCLUDED.submission_deadline,
    mail_lead_months_snapshot = EXCLUDED.mail_lead_months_snapshot;

-- patent_review_history는 seed에서 미리 만들지 않는다.
-- 분기 활성화 시 백엔드가 patents.patent_status = ACTIVE 인 특허를 대상으로
-- REVIEW_QUARTER_STARTED 이력을 생성하고 patents.is_in_review=true로 전환한다.
