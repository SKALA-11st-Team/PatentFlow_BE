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

-- 관리자(ADMIN) 계정은 PATENTFLOW_BOOTSTRAP_ADMIN_* 환경변수로 생성한다.
-- BootstrapAdminInitializer가 시작 시 자동 생성하므로 여기서는 BUSINESS 사용자만 시드한다.
INSERT INTO users (id, email, password, role, department_id, username, created_at) VALUES
    ('USER-rnd-manager', 'rnd.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-RND', 'R&D 담당자', CURRENT_TIMESTAMP),
    ('USER-platform-manager', 'platform.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-PLATFORM', '플랫폼 담당자', CURRENT_TIMESTAMP),
    ('USER-esg-manager', 'esg.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-ESG', 'ESG 담당자', CURRENT_TIMESTAMP),
    ('USER-ict-manager', 'ict.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-ICT', 'ICT 담당자', CURRENT_TIMESTAMP),
    ('USER-mfg-manager', 'mfg.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-MFG', '제조 담당자', CURRENT_TIMESTAMP),
    ('USER-biz-manager', 'biz.manager@syuuk.test', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-BIZ', '사업기획 담당자', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    password = EXCLUDED.password,
    role = EXCLUDED.role,
    department_id = EXCLUDED.department_id,
    username = EXCLUDED.username;

INSERT INTO system_settings (setting_key, setting_value, updated_at) VALUES
    ('country.extension.KR', '12', CURRENT_TIMESTAMP),
    ('country.extension.JP', '12', CURRENT_TIMESTAMP),
    ('country.extension.CN', '12', CURRENT_TIMESTAMP),
    ('country.extension.US', '12', CURRENT_TIMESTAMP),
    ('country.extension.TW', '12', CURRENT_TIMESTAMP)
ON CONFLICT (setting_key) DO UPDATE SET
    setting_value = EXCLUDED.setting_value,
    updated_at = EXCLUDED.updated_at;


-- ============================================================
-- 분기 설정: Q1(완료), Q2(지연, is_delayed=true), Q3(진행중)
-- 검토 시작일 = 납부기간 시작일 - 2개월
-- ============================================================
INSERT INTO quarter_settings (
    quarter_key, setting_year, quarter_number, start_date, end_date,
    activated, activated_at, ended, ended_at, submission_deadline, mail_lead_months_snapshot
) VALUES
    -- Q1: 검토기간 2025-11-01 ~ 2026-01-01, 완료
    ('2026-Q1', 2026, 1, DATE '2026-01-01', DATE '2026-03-31',
     true, TIMESTAMP WITH TIME ZONE '2025-11-01 09:00:00+09',
     true, TIMESTAMP WITH TIME ZONE '2026-03-31 18:00:00+09',
     DATE '2025-12-01', 2),
    -- Q2: 검토기간 2026-02-01 ~ 2026-04-01, 활성화됨 (납부기간 시작일 2026-04-01 경과 → is_delayed=true)
    ('2026-Q2', 2026, 2, DATE '2026-04-01', DATE '2026-06-30',
     true, TIMESTAMP WITH TIME ZONE '2026-02-01 09:00:00+09',
     false, NULL, DATE '2026-03-01', 2),
    -- Q3: 검토기간 2026-05-01 ~ 2026-07-01, 현재 진행중
    ('2026-Q3', 2026, 3, DATE '2026-07-01', DATE '2026-09-30',
     true, TIMESTAMP WITH TIME ZONE '2026-05-01 09:00:00+09',
     false, NULL, DATE '2026-06-01', 2),
    -- Q4: 미활성
    ('2026-Q4', 2026, 4, DATE '2026-10-01', DATE '2026-12-31',
     false, NULL, false, NULL, NULL, NULL)
ON CONFLICT (quarter_key) DO UPDATE SET
    setting_year = EXCLUDED.setting_year, quarter_number = EXCLUDED.quarter_number,
    start_date = EXCLUDED.start_date, end_date = EXCLUDED.end_date,
    activated = EXCLUDED.activated, activated_at = EXCLUDED.activated_at,
    ended = EXCLUDED.ended, ended_at = EXCLUDED.ended_at,
    submission_deadline = EXCLUDED.submission_deadline,
    mail_lead_months_snapshot = EXCLUDED.mail_lead_months_snapshot;
