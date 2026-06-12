-- AI 레포트 실패/제한 생성인데 MAIL_READY로 전이된 기존 데이터 보정용 1회 SQL.
-- 적용 대상: 최신 review history가 MAIL_READY이고, AI 레포트가 degraded이거나 failure reason을 가진 행.
-- 제외 대상: 이미 메일 발송/사업부 회신/최종 판단 단계로 진행된 행은 status 조건상 변경되지 않는다.

WITH latest_history AS (
    SELECT h.*
    FROM patentflow.patent_review_history h
    JOIN (
        SELECT patent_id, max(created_at) AS latest_created_at
        FROM patentflow.patent_review_history
        GROUP BY patent_id
    ) latest
      ON latest.patent_id = h.patent_id
     AND latest.latest_created_at = h.created_at
),
target_history AS (
    SELECT id, patent_id, ai_report_id, ai_failure_reason
    FROM latest_history
    WHERE review_workflow_status = 'MAIL_READY'
      AND (
          ai_degraded = true
          OR nullif(trim(coalesce(ai_failure_reason, '')), '') IS NOT NULL
      )
),
updated_history AS (
    UPDATE patentflow.patent_review_history h
       SET review_workflow_status = 'REVIEW_QUARTER_STARTED'
      FROM target_history t
     WHERE h.id = t.id
     RETURNING h.patent_id, h.ai_report_id, h.ai_failure_reason
),
latest_jobs AS (
    SELECT j.*
    FROM patentflow.patent_ai_report_jobs j
    JOIN (
        SELECT patent_id, max(requested_at) AS latest_requested_at
        FROM patentflow.patent_ai_report_jobs
        GROUP BY patent_id
    ) latest
      ON latest.patent_id = j.patent_id
     AND latest.latest_requested_at = j.requested_at
)
UPDATE patentflow.patent_ai_report_jobs j
   SET status = 'DEGRADED',
       message = coalesce(nullif(trim(u.ai_failure_reason), ''), 'AI 평가가 제한된 근거로 생성되었습니다.')
  FROM updated_history u,
       latest_jobs lj
 WHERE j.job_id = lj.job_id
   AND lj.patent_id = u.patent_id
   AND lj.status = 'SUCCEEDED'
   AND (u.ai_report_id IS NULL OR lj.report_id = u.ai_report_id);
