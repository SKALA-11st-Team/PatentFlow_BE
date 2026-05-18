# Backend Handoff

## 목적

FE/BE API 인터페이스 정합성 점검 후 주요 불일치 지점을 수정했다. 이 문서는 백엔드 담당자가 변경 의도와 FE 연동 기대값을 빠르게 확인하기 위한 인수 문서다.

## 변경 요약

- 사업부 체크리스트 제출 DTO에 Bean Validation을 추가했다.
  - `responses`: 빈 배열 불가, 각 응답 객체 검증
  - `responses[].itemId`: 필수
  - `responses[].score`: 1~4 범위
  - `qualitativeScore`: -5~5 범위
  - `finalOpinion`: 필수
- 메일 발송 API가 현재 `MAIL_READY` 상태인 특허만 `WAITING_BUSINESS_RESPONSE`로 변경하도록 제한했다.
- 메일 발송 응답에 `skippedPatentIds`를 추가했다.
- 관리자 최종 판단 API는 `BUSINESS_RESPONSE_RECEIVED` 상태에서만 처리하도록 제한했다.
- `APPROVED_MAINTAIN` 최종 판단 시 `legalActionResult`를 `MAINTAINED`로 기록하도록 보정했다.
- fixture의 사업부 의견 mock 데이터가 실제 workflow 상태와 맞도록 조정했다.
- 컨트롤러 테스트에 실패/스킵 케이스를 추가하고 기존 테스트 indent를 정리했다.

## API 계약

### POST `/api/v1/mailings/send`

요청:

```json
{
  "patentIds": ["PAT-2026-0003", "PAT-2026-0001"]
}
```

응답:

```json
{
  "data": {
    "updatedCount": 1,
    "updatedPatentIds": ["PAT-2026-0003"],
    "skippedPatentIds": ["PAT-2026-0001"]
  }
}
```

처리 규칙:

- `MAIL_READY`인 특허만 `WAITING_BUSINESS_RESPONSE`로 변경한다.
- 존재하지 않거나 현재 단계가 맞지 않는 ID는 `skippedPatentIds`에 포함한다.
- FE는 `skippedPatentIds`가 있을 경우 "현재 단계가 맞지 않아 건너뜀" 메시지를 노출한다.

### POST `/api/v1/patents/{patentId}/business-submissions`

주요 검증:

- path의 `patentId`와 body의 `patentId`가 다르면 `INVALID_REQUEST`를 반환한다.
- `responses`는 1개 이상이어야 한다.
- 각 `score`는 1~4 범위여야 한다.
- `finalOpinion`은 `MAINTAIN` 또는 `ABANDON`이어야 한다.
- 정상 제출 후 특허 상세의 `businessOpinion`이 갱신되고 workflow는 `BUSINESS_RESPONSE_RECEIVED`로 진행된다.

### POST `/api/v1/patents/executive-approvals/bulk-decision`

요청:

```json
{
  "patentIds": ["PAT-2026-0005"],
  "decision": "APPROVED_ABANDON"
}
```

응답:

```json
{
  "data": {
    "decision": "APPROVED_ABANDON",
    "updatedCount": 1,
    "updatedPatentIds": ["PAT-2026-0005"]
  }
}
```

처리 규칙:

- 허용 상태: `BUSINESS_RESPONSE_RECEIVED`
- 그 외 상태에서는 `409 CONFLICT`, `INVALID_WORKFLOW_STATUS`를 반환한다.
- decision별 `legalActionResult` 매핑:
  - `APPROVED_MAINTAIN` -> `MAINTAINED`
  - `APPROVED_ABANDON` -> `ABANDONED`
  - `APPROVED_SELL` -> `SOLD`
  - `REJECTED`, `REQUEST_CHANGES` -> `null`

## FE 연동 기대값

- FE는 백엔드 에러 응답의 `message`, `code`, `details`를 파싱해 사용자 메시지로 표시한다.
- 관리자 특허 상세 화면은 `BUSINESS_RESPONSE_RECEIVED` 상태에서만 최종 판단 버튼을 활성화한다.
- 최종 판단 저장 후 FE는 특허 상세와 히스토리를 다시 조회한다.
- 관리자 검토 대상 화면은 메일 발송 후 전체 목록을 다시 조회해 상태 전환 결과를 반영한다.
- 목록 API는 `page` 또는 `size`가 전달되면 해당 페이지 데이터만 사용한다. 쿼리가 없으면 대시보드/집계용으로 전체 페이지를 순회 조회한다.

## 검증

- BE: `mvn test`
  - Tests run: 17
  - Failures: 0
  - Errors: 0
- FE: `npm run lint`
- FE: `npm run build`

## 남은 확인 포인트

- 현재 BE는 in-memory fixture 기반이므로 실제 DB 연동 시 동일한 workflow guard와 skipped 처리 규칙을 repository/service 계층에 유지해야 한다.
- 최종 판단 API는 부분 성공을 지원하지 않는다. 일괄 처리 중 하나라도 유효하지 않은 상태면 전체 요청이 실패한다. 운영 UX에서 부분 성공이 필요하면 응답 계약을 별도로 확장해야 한다.
- `skippedPatentIds`는 FE에서 optional로 수용하고 있으나, BE에서는 계속 배열로 반환하는 계약을 유지하는 편이 좋다.
