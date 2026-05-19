# DB Seed And Status Update Plan

## 현재 추가한 Seed

- `src/main/resources/db/seed/skax_patents.sql`
  - 원본: `docs/skax_patent_list.csv`
  - 대상 테이블: `patents`
  - 건수: 185건
  - `fee_due_date`: 2026-05-19 기준 다음 연차료 점검일로 계산
  - `patent_status`: CSV 상태를 기준으로 매핑하되, 예상 소멸일이 기준일 이전이면 `EXPIRED`
- `src/main/resources/db/seed/core_review_workflow_seed.sql`
  - 기본 부서, 관리자/사업부 사용자, 국가별 설정, 2026년 분기 설정을 seed한다.
  - `patents` 기준으로 2026-Q2 `patent_review_history`를 생성한다.
  - 2026-Q2 연차료 점검 대상은 `REVIEW_QUARTER_STARTED`, 나머지는 `NOT_IN_REVIEW_QUARTER`로 시작한다.

## 추가로 필요한 Seed

1. `business_submissions`, `mailing_history`
   - 데모/통합 테스트에서 사업부 제출 이력과 메일링 이력을 보여주려면 선택 seed가 필요하다.
   - 운영 초기 데이터에는 보통 넣지 않고, 데모 프로필 또는 테스트 프로필로 분리하는 편이 안전하다.

2. AI 평가 기준 seed
   - 평가 지표 외부화를 진행하면 `evaluation_criteria`, `evaluation_weights` 같은 설정 테이블 seed가 필요하다.

## 필요한 DB 함수/트리거 후보

1. `calculate_next_fee_due_date(country, application_date, registration_date, expected_expiration_date, base_date)`
   - 특허별 다음 연차료 점검일을 계산한다.
   - KR/JP/CN/TW/기타 국가는 출원일 기준 매년 도래일을 사용한다.
   - US는 등록일 기준 3.5년, 7.5년, 11.5년 maintenance fee 일정을 별도 처리한다.
   - 예상 소멸일 이후 날짜는 반환하지 않도록 제한한다.

2. `refresh_patent_lifecycle_status(base_date)`
   - 예상 소멸일이 지난 `ACTIVE` 특허를 `EXPIRED`로 갱신한다.
   - `ABANDONED`, `SOLD`처럼 사람이 확정한 상태는 자동으로 되돌리지 않는다.

3. `create_quarter_review_targets(quarter_key, start_date, end_date)`
   - 해당 분기 안에 `fee_due_date`가 들어오는 특허를 `patent_review_history`에 upsert한다.
   - 초기 상태는 `REVIEW_QUARTER_STARTED`가 적절하다.
   - 담당 부서와 연차료 점검일은 당시 값을 snapshot으로 저장한다.

4. `advance_fee_due_date_after_maintenance(patent_id, decided_at)`
   - 최종 판단이 `MAINTAINED`로 기록되면 다음 연차료 점검일을 다시 계산해 `patents.fee_due_date`를 갱신한다.
   - 최종 판단 이력은 `patent_review_history`에 남기고, 원장에는 다음 점검일만 반영한다.

5. `set_updated_at()`
   - SQL seed나 DB 콘솔에서 직접 update할 때도 `updated_at`이 갱신되도록 공통 trigger function을 둔다.
   - JPA Auditing만 믿으면 애플리케이션 밖에서 변경한 row의 시간이 누락될 수 있다.

## 적용 방식 제안

- 상태 전환처럼 비즈니스 의미가 큰 작업은 DB trigger보다 Spring service 또는 scheduled job에서 명시적으로 호출하는 편이 안전하다.
- DB에는 계산 함수와 무결성 보조 trigger를 두고, `검토 분기 활성화`, `최종 판단 기록`, `연차료 다음 점검일 갱신`은 애플리케이션 서비스에서 트랜잭션으로 묶는 것을 권장한다.
- 추후 Flyway 또는 Liquibase를 도입하면 seed와 함수 정의를 버전 관리할 수 있다.
