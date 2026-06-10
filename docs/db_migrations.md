# DB 수동 마이그레이션 노트

`spring.jpa.hibernate.ddl-auto=update`는 **새 테이블/컬럼 추가만** 반영하고 기존 컬럼의
타입 변경은 수행하지 않는다. 아래 변경은 기존 데이터베이스에 수동 적용이 필요하다.
(신규 DB는 엔티티 정의대로 생성되므로 적용 불필요. varchar→text는 PostgreSQL에서
메타데이터만 바꾸는 안전한 변경이다.)

## 2026-06-11 — 길이 제한 텍스트 컬럼 TEXT 전환

LLM 생성 텍스트(요약 markdown 등)와 사용자 자유 입력이 varchar(1000~2000) 컬럼에
저장되어 길이 초과 시 INSERT/UPDATE가 실패하던 결함 수정.
특히 `ai_recommendation_reason`에는 요약 markdown 전문이 그대로 저장된다.

```sql
ALTER TABLE patentflow.patent_review_history
    ALTER COLUMN ai_recommendation_reason TYPE TEXT,
    ALTER COLUMN ai_failure_reason TYPE TEXT,
    ALTER COLUMN summary_text TYPE TEXT,
    ALTER COLUMN summary_problem_solved TYPE TEXT,
    ALTER COLUMN summary_claims TYPE TEXT,
    ALTER COLUMN business_opinion_reason TYPE TEXT,
    ALTER COLUMN final_decision_reason TYPE TEXT;

ALTER TABLE patentflow.patent_ai_report_jobs
    ALTER COLUMN message TYPE TEXT;

ALTER TABLE patentflow.notifications
    ALTER COLUMN message TYPE TEXT;
```
