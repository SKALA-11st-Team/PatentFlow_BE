-- PatentFlow local/demo workflow seed
-- This script creates presentation-friendly workflow states and histories.
-- It is intentionally loaded only by LocalDemoSeedRunner for local/demo profiles.

INSERT INTO users (id, username, password, role, department_id, display_name, created_at) VALUES
    ('USER-business-demo', 'business', '$2a$10$8Cs9O/CKSYzHkTU4/5WBguCSVaE0fWcP8w3pizKrhkoGNOT7nl78e', 'BUSINESS', 'DEPT-ICT', '사업부 데모 담당자', CURRENT_TIMESTAMP)
ON CONFLICT (username) DO UPDATE SET
    password = EXCLUDED.password,
    role = EXCLUDED.role,
    department_id = EXCLUDED.department_id,
    display_name = EXCLUDED.display_name;

WITH demo_reviews (
    patent_id,
    workflow_status,
    ai_recommendation,
    ai_reason,
    ai_total_score,
    summary_text,
    summary_problem,
    summary_points,
    summary_claims,
    business_decision,
    business_reason,
    business_submitted_at,
    legal_result,
    final_reason,
    final_decided_at
) AS (
    VALUES
        ('PAT-2026-0028', 'MAIL_READY', 'MAINTAIN',
         'ESG 경영관리 플랫폼과 직접 연결되는 특허로 유지 검토 가치가 높습니다.', 82,
         'ESG 경영관리 플랫폼 운영 과정에서 지표 수집, 평가, 보고 흐름을 시스템화하는 특허입니다.',
         'ESG 데이터가 여러 조직과 문서에 흩어져 관리되는 문제를 줄입니다.',
         '["ESG 지표 수집 자동화","평가 결과 관리","보고 workflow 연계"]',
         '플랫폼 운영 절차와 데이터 처리 흐름을 함께 보호하는 권리범위입니다.',
         NULL, NULL, NULL, NULL, NULL, NULL),
        ('PAT-2026-0069', 'MAIL_READY', 'REVIEW_AGAIN',
         '디지털 트윈 적용 가능성은 있으나 실제 사업 적용 범위 확인이 필요합니다.', 68,
         '반도체 제조물을 가상 환경에 복제해 공정 상태를 검토할 수 있게 하는 디지털 트윈 특허입니다.',
         '실제 설비 변경 전 가상 검증을 통해 공정 리스크를 낮추는 데 초점이 있습니다.',
         '["가상 반도체 제조물","공정 시뮬레이션","설비 검증"]',
         '제조물 복제와 가상 검증 절차를 중심으로 한 권리범위입니다.',
         NULL, NULL, NULL, NULL, NULL, NULL),
        ('PAT-2026-0071', 'MAIL_READY', 'MAINTAIN',
         '반도체 공정 리스크 제어와 연결되어 유지 검토 가치가 있습니다.', 76,
         '장비 신뢰지수와 Lot 리스크 스코어를 이용해 계측 제어를 동적으로 조정하는 특허입니다.',
         '장비 상태에 따라 계측 우선순위를 조정해 품질 리스크를 줄입니다.',
         '["장비 신뢰지수","Lot 리스크 스코어","동적 계측 제어"]',
         '리스크 산정과 계측 제어 조건을 결합한 권리범위입니다.',
         NULL, NULL, NULL, NULL, NULL, NULL),
        ('PAT-2026-0076', 'WAITING_BUSINESS_RESPONSE', 'REVIEW_AGAIN',
         '서비스 실험 플랫폼과 연결 가능하나 현업 활용 여부 확인이 필요합니다.', 64,
         'AB 테스트 결과를 기반으로 서비스 제공 방식을 비교하고 개선하는 특허입니다.',
         '여러 서비스안을 빠르게 검증해 운영 의사결정에 활용합니다.',
         '["AB 테스트","서비스안 비교","실험 결과 기반 운영"]',
         '테스트군 구성과 결과 기반 서비스 제공 절차를 포함합니다.',
         NULL, NULL, NULL, NULL, NULL, NULL),
        ('PAT-2026-0097', 'BUSINESS_RESPONSE_RECEIVED', 'ABANDON',
         '음성 인증 시장성과 내부 활용도가 낮아 포기 검토가 필요합니다.', 49,
         '목소리 특징 정보를 활용해 본인 인증을 의뢰하고 대행하는 방식의 특허입니다.',
         '사용자 인증 과정에서 음성 특징 정보를 인증 수단으로 활용합니다.',
         '["음성 특징 추출","본인 인증 의뢰","인증 대행"]',
         '음성 특징 기반 인증 요청과 대행 처리 절차를 중심으로 합니다.',
         'ABANDON', '현재 사업부 서비스 로드맵과 직접 연결되는 적용 계획이 없어 포기 의견을 제출합니다.',
         TIMESTAMP WITH TIME ZONE '2026-05-17 14:30:00+09', NULL, NULL, NULL),
        ('PAT-2026-0073', 'BUSINESS_RESPONSE_RECEIVED', 'ABANDON',
         '해외 권리 유지 비용 대비 내부 활용 계획이 낮아 포기 검토가 필요합니다.', 52,
         '장비 신뢰지수를 활용한 Lot 계측 제어 기술의 중국 등록 특허입니다.',
         '공정 품질 관리를 위한 계측 제어 방식을 해외 권리로 보호합니다.',
         '["중국 등록 권리","Lot 계측 제어","공정 리스크 관리"]',
         '장비 신뢰도 기반 계측 조건 제어를 해외 권리로 보호합니다.',
         'ABANDON', '국내 특허를 우선 유지하고 해외 권리는 비용 대비 활용도가 낮아 포기 의견입니다.',
         TIMESTAMP WITH TIME ZONE '2026-05-16 16:10:00+09', NULL, NULL, NULL),
        ('PAT-2026-0124', 'LEGAL_ACTION_RECORDED', 'MAINTAIN',
         '보안 관리 권한 획득 절차와 관련되어 유지 가치가 있습니다.', 79,
         '모바일 환경에서 안전하게 관리자 권한을 획득하고 시스템에 접근하는 방법에 대한 특허입니다.',
         '관리 권한 접근 과정에서 보안 이슈를 줄이고 인증 흐름을 명확히 합니다.',
         '["모바일 관리 권한","보안 접근","관리자 인증"]',
         '모바일 기반 관리 권한 획득과 인증 절차를 포함합니다.',
         'MAINTAIN', '보안 운영 업무와 연결되어 유지 의견을 제출합니다.',
         TIMESTAMP WITH TIME ZONE '2026-05-15 10:20:00+09',
         'MAINTAINED', '보안 운영 관련 활용 가능성이 있어 유지 처리합니다.',
         TIMESTAMP WITH TIME ZONE '2026-05-18 11:00:00+09'),
        ('PAT-2026-0082', 'LEGAL_ACTION_RECORDED', 'ABANDON',
         '내부 활용 가능성이 낮아 포기 검토 대상으로 관리합니다.', 57,
         'IT 서비스 구축 사업의 리스크를 관리하기 위한 절차와 시스템에 관한 특허입니다.',
         '프로젝트 수행 과정의 리스크를 구조화해 관리하는 데 목적이 있습니다.',
         '["IT 프로젝트 리스크","위험 관리","관리 시스템"]',
         '리스크 항목 식별과 관리 절차를 포함한 권리범위입니다.',
         'ABANDON', '현 사업 전략과 직접 연결성이 낮아 포기 의견입니다.',
         TIMESTAMP WITH TIME ZONE '2026-05-14 15:40:00+09',
         'ABANDONED', '사업부 포기 의견과 AI 평가 근거를 반영해 포기 처리합니다.',
         TIMESTAMP WITH TIME ZONE '2026-05-18 15:20:00+09'),
        ('PAT-2026-0127', 'WAITING_BUSINESS_RESPONSE', 'HOLD',
         '인증 분야 활용 가능성 확인을 위해 사업부 검토가 필요합니다.', 61,
         '여러 통신채널과 디바이스, 난수를 조합해 사용자를 인증하는 특허입니다.',
         '단일 인증 수단의 취약성을 보완하고 다중 인증 흐름을 제공합니다.',
         '["멀티 채널 인증","다중 난수","사용자 인증"]',
         '통신채널, 디바이스, 난수를 조합한 인증 절차를 포함합니다.',
         NULL, NULL, NULL, NULL, NULL, NULL)
)
UPDATE patent_review_history h
SET
    review_workflow_status = d.workflow_status,
    ai_recommendation = d.ai_recommendation,
    ai_report_id = 'EVAL-DEMO-' || d.patent_id,
    ai_report_created_at = TIMESTAMP WITH TIME ZONE '2026-05-14 09:00:00+09',
    ai_recommendation_reason = d.ai_reason,
    ai_total_score = d.ai_total_score,
    ai_scores_json = jsonb_build_array(
        jsonb_build_object('category', 'RIGHTS', 'score', LEAST(d.ai_total_score + 3, 95), 'evidence', '등록 상태와 청구항 범위를 기준으로 권리 안정성을 검토했습니다.'),
        jsonb_build_object('category', 'TECHNOLOGY', 'score', LEAST(d.ai_total_score + 5, 95), 'evidence', '명세서의 기술 구성과 구현 가능성을 중심으로 검토했습니다.'),
        jsonb_build_object('category', 'MARKET', 'score', GREATEST(d.ai_total_score - 8, 35), 'evidence', '시장 적용 가능성과 외부 수요 가능성을 함께 확인했습니다.'),
        jsonb_build_object('category', 'BUSINESS_ALIGNMENT', 'score', d.ai_total_score, 'evidence', '관련 사업 분야와 내부 제품 연계 가능성을 기준으로 판단했습니다.')
    )::text,
    ai_missing_information_json = '["최신 제품 적용 여부","경쟁사 활용 사례"]',
    summary_text = d.summary_text,
    summary_problem_solved = d.summary_problem,
    summary_core_technical_points_json = d.summary_points,
    summary_claims = d.summary_claims,
    summary_missing_fields_json = '["현업 적용 범위","최근 매출 기여도"]',
    business_opinion_decision = d.business_decision,
    business_opinion_reason = d.business_reason,
    business_opinion_submitted_at = d.business_submitted_at,
    legal_action_result = d.legal_result,
    final_decision_id = CASE WHEN d.legal_result IS NULL THEN NULL ELSE d.patent_id || '-DEMO-DEC-01' END,
    final_decision_reason = d.final_reason,
    final_decision_decided_at = d.final_decided_at,
    updated_at = CURRENT_TIMESTAMP
FROM demo_reviews d
WHERE h.patent_id = d.patent_id
  AND h.quarter_key = '2026-Q2';

INSERT INTO mailing_history (
    mailing_id,
    body,
    cc_emails_json,
    patent_count,
    patents_json,
    recipient_email,
    recipient_name,
    department_id,
    sent_at,
    sent_by,
    status,
    subject
) VALUES
    (
        'MAIL-DEMO-ICT-01',
        '2026년 2분기 연차료 검토 대상 특허에 대한 사업부 의견 요청드립니다.',
        '["security.cc@syuuk.test"]',
        3,
        '[{"patentId":"PAT-2026-0076","managementNumber":"P201603002-KR0","title":"AB 테스팅 기반 서비스 제공 방법 및 시스템"},{"patentId":"PAT-2026-0097","managementNumber":"P201501001-KR0","title":"목소리 특징 정보를 이용한 본인 인증 의뢰 및 대행 방법"},{"patentId":"PAT-2026-0127","managementNumber":"P201203001-KR0","title":"멀티 통신채널, 멀티 디바이스 및 멀티 난수에 의한 멀티 인증 시스템 및 방법"}]',
        'business',
        '사업부 데모 담당자',
        'DEPT-ICT',
        TIMESTAMP WITH TIME ZONE '2026-05-14 13:10:00+09',
        'PatentFlow',
        'RECORDED',
        '[PatentFlow] 2026년 2분기 ICT사업부 특허 검토 요청'
    ),
    (
        'MAIL-DEMO-MFG-01',
        '반도체 공정 관련 특허의 유지 여부 검토를 요청드립니다.',
        '[]',
        2,
        '[{"patentId":"PAT-2026-0069","managementNumber":"P201704001-KR0","title":"실제 반도체 제조물을 복제한 가상 반도체 제조물 제공 방법 및 시스템"},{"patentId":"PAT-2026-0073","managementNumber":"P201702001-CN0","title":"用于基于设备可靠性指数控制基于批次风险分数的动态批次测量的方法和系统"}]',
        'mfg.manager@syuuk.test',
        '제조 담당자',
        'DEPT-MFG',
        TIMESTAMP WITH TIME ZONE '2026-05-14 13:25:00+09',
        'PatentFlow',
        'RECORDED',
        '[PatentFlow] 제조사업부 특허 검토 요청'
    )
ON CONFLICT (mailing_id) DO NOTHING;

WITH demo_business_submissions (
    patent_id,
    submitted_by,
    checklist_total,
    qualitative_score,
    checklist_scores_json
) AS (
    VALUES
    (
        'PAT-2026-0097',
        '사업부 데모 담당자',
        5,
        -3,
        '[{"itemId":"TECH_COMPLETENESS","score":2,"memo":"관련 구현 계획이 없습니다."},{"itemId":"TECH_ORIGINALITY","score":2,"memo":"대체 인증 기술이 많습니다."},{"itemId":"MARKETABILITY","score":2,"memo":"현재 시장 적용 가능성이 낮습니다."},{"itemId":"EXPECTED_EFFECT","score":2,"memo":"비용 절감 효과가 제한적입니다."}]'
    ),
    (
        'PAT-2026-0073',
        '제조 담당자',
        7,
        -3,
        '[{"itemId":"TECH_COMPLETENESS","score":3,"memo":"기술 자체는 활용 가능성이 있습니다."},{"itemId":"TECH_ORIGINALITY","score":2,"memo":"국내 권리로도 방어 가능합니다."},{"itemId":"MARKETABILITY","score":2,"memo":"중국 권리 활용 계획은 낮습니다."},{"itemId":"EXPECTED_EFFECT","score":3,"memo":"유지 효과는 제한적입니다."}]'
    ),
    (
        'PAT-2026-0124',
        '사업부 데모 담당자',
        18,
        3,
        '[{"itemId":"TECH_COMPLETENESS","score":4,"memo":"운영 적용 가능성이 높습니다."},{"itemId":"TECH_ORIGINALITY","score":4,"memo":"보안 접근 흐름이 명확합니다."},{"itemId":"MARKETABILITY","score":3,"memo":"유사 보안 요구가 지속됩니다."},{"itemId":"EXPECTED_EFFECT","score":4,"memo":"운영 리스크 절감 효과가 있습니다."}]'
    ),
    (
        'PAT-2026-0082',
        '사업기획 담당자',
        8,
        -3,
        '[{"itemId":"TECH_COMPLETENESS","score":3,"memo":"일반 프로젝트 관리 영역입니다."},{"itemId":"TECH_ORIGINALITY","score":2,"memo":"차별성은 제한적입니다."},{"itemId":"MARKETABILITY","score":3,"memo":"외부 이전 가능성은 있습니다."},{"itemId":"EXPECTED_EFFECT","score":3,"memo":"내부 직접 효과는 낮습니다."}]'
    )
)
UPDATE patent_review_history h
SET
    business_opinion_submitted_by = d.submitted_by,
    business_checklist_total = d.checklist_total,
    business_qualitative_score = d.qualitative_score,
    business_checklist_scores_json = d.checklist_scores_json,
    business_ai_report_created_at = h.ai_report_created_at,
    business_ai_recommendation = h.ai_recommendation,
    business_ai_total_score = h.ai_total_score,
    updated_at = CURRENT_TIMESTAMP
FROM demo_business_submissions d
WHERE h.patent_id = d.patent_id
  AND h.quarter_key = '2026-Q2';
