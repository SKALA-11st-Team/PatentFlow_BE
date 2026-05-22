# PatentFlow BE Notes

이 파일의 예전 상세 명세는 현재 구현과 맞지 않아 인덱스 문서로 축소했다.

현재 BE 작업 시 우선 확인할 문서:

- `AGENTS.md`
- `docs/PatentFlow_FR_mapping.md`
- `docs/prompt.md`
- `docs/db_seed_and_status_plan.md`

현재 유지해야 하는 핵심 계약:

- AI 결과는 최종 판단이 아니라 평가 레포트와 권고안이다.
- 최종 판단과 실제 법무 처리 결과는 관리자/법무 사용자가 별도 기록한다.
- 임원 승인 actor와 임원 승인 workflow는 사용하지 않는다.
- AI 레포트 생성 성공 후 workflow 상태는 `REPORT_GENERATED`를 거치지 않고 바로 `MAIL_READY`가 된다.
