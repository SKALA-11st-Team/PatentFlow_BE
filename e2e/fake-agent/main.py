"""PatentFlow_Agent의 BE-facing 계약만 모사하는 E2E용 가짜 에이전트.

실제 계약 원본: PatentFlow_Agent/app/api.py
BE 호출 지점:   PatentFlow_BE .../patent/client/AiReportAgentClient.java

LLM/외부 검색 없이 고정 fixture를 반환한다. FAKE_AGENT_DELAY_SECONDS 동안
단계 진행(progress)을 갱신해 FE의 생성 중 폴링 UI를 실제로 거치게 한다.
"""
from __future__ import annotations

import hashlib
import json
import os
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException

app = FastAPI(title="PatentFlow fake agent (e2e)")

FIXTURE: dict[str, Any] = json.loads(
    (Path(__file__).parent / "fixtures" / "evaluate_response.json").read_text(encoding="utf-8")
)
DELAY_SECONDS = float(os.getenv("FAKE_AGENT_DELAY_SECONDS", "3"))
# true면 특허별 첫 evaluate 호출은 degraded 응답을 반환한다.
# → 분기 활성화 자동 배치(특허당 1회)는 모든 대상을 REVIEW_QUARTER_STARTED에 남기고
#   (degraded는 MAIL_READY로 전이하지 않는 제품 동작), 이후 수동 재요청은 성공한다.
#   journey가 수동 생성 버튼·실패 표면화·재시도 경로를 결정적으로 검증할 수 있게 해준다.
DEGRADE_FIRST_CALL = os.getenv("FAKE_AGENT_DEGRADE_FIRST_CALL", "false").lower() == "true"
_call_counts: dict[str, int] = {}

# 진행 단계 — 실제 agent의 progress_registry 계약(stage/stageLabel/updatedAt)과 동일한 필드명.
STAGES: list[tuple[str, str]] = [
    ("evidence_collection", "근거 수집"),
    ("evidence_compression", "근거 압축"),
    ("valuation", "4축 채점"),
    ("report_writing", "레포트 작성"),
]

_progress: dict[str, dict[str, str]] = {}

AXES = {
    "legal": "권리성",
    "technology": "기술성",
    "market": "시장성",
    "business_fit": "사업 연계성",
}
_prompts: dict[str, dict[str, Any]] = {
    axis: {
        "axis": axis,
        "label": label,
        "path": f"prompts/valuation/{axis}.md",
        "markdown": f"# {label} 평가 기준 (e2e fixture)\n\n- 고정 기준 텍스트",
    }
    for axis, label in AXES.items()
}


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _prompt_response(axis: str) -> dict[str, Any]:
    entry = _prompts[axis]
    return {
        **entry,
        "checksum": hashlib.sha256(entry["markdown"].encode("utf-8")).hexdigest(),
        "updatedAt": _now(),
    }


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/api/v1/ai/patents/{patent_id}/evaluate")
def evaluate(patent_id: str, request: dict[str, Any]) -> dict[str, Any]:
    _call_counts[patent_id] = _call_counts.get(patent_id, 0) + 1
    if DEGRADE_FIRST_CALL and _call_counts[patent_id] == 1:
        return {
            **FIXTURE,
            "patentId": patent_id,
            "scores": [],
            "recommendation": "HOLD",
            "summaryMarkdown": None,
            "valuationReportMarkdown": None,
            "totalScore": None,
            "averageScore": None,
            "finalGrade": None,
            "finalIndicator": None,
            "degraded": True,
            "failureReason": "e2e fixture: 첫 호출 의도적 degraded (재요청 시 성공)",
            "generatedAt": _now(),
        }
    per_stage = max(DELAY_SECONDS, 0.0) / len(STAGES)
    for stage, label in STAGES:
        _progress[patent_id] = {"stage": stage, "stageLabel": label, "updatedAt": _now()}
        time.sleep(per_stage)
    _progress[patent_id] = {"stage": "completed", "stageLabel": "완료", "updatedAt": _now()}

    body = dict(FIXTURE)
    body["patentId"] = patent_id
    body["generatedAt"] = _now()
    # 계약 C1: BE가 보낸 가치평가 기준을 그대로 스냅샷으로 되돌려준다.
    if request.get("valuationConfig") is not None:
        body["appliedValuationConfig"] = request["valuationConfig"]
    return body


@app.get("/api/v1/ai/patents/{patent_id}/evaluate/progress")
def progress(patent_id: str) -> dict[str, str]:
    entry = _progress.get(patent_id)
    if not entry:
        raise HTTPException(status_code=404, detail="no progress")
    return {"patentId": patent_id, **entry}


@app.post("/api/v1/ai/patents/{patent_id}/recommend-fields")
def recommend_fields(patent_id: str, request: dict[str, Any]) -> dict[str, Any]:
    taxonomy = request.get("taxonomy") or {}
    business = (taxonomy.get("business") or ["AI"])[0]
    technology = (taxonomy.get("technology") or ["자연어처리"])[0]
    return {
        "businessArea": business,
        "technologyArea": technology,
        "confidence": 0.92,
        "confidenceText": "높음",
        "reason": "e2e fixture 추천 — 제목 키워드 기반(가짜)",
    }


@app.get("/api/v1/admin/valuation-criteria/prompts")
def list_prompts() -> list[dict[str, Any]]:
    return [_prompt_response(axis) for axis in AXES]


@app.get("/api/v1/admin/valuation-criteria/prompts/{axis}")
def get_prompt(axis: str) -> dict[str, Any]:
    if axis not in _prompts:
        raise HTTPException(status_code=404, detail=f"unknown axis: {axis}")
    return _prompt_response(axis)


@app.put("/api/v1/admin/valuation-criteria/prompts/{axis}")
def put_prompt(axis: str, request: dict[str, Any]) -> dict[str, Any]:
    if axis not in _prompts:
        raise HTTPException(status_code=404, detail=f"unknown axis: {axis}")
    markdown = (request.get("markdown") or "").strip()
    if not markdown:
        raise HTTPException(status_code=400, detail="markdown is required")
    _prompts[axis]["markdown"] = markdown
    return _prompt_response(axis)
