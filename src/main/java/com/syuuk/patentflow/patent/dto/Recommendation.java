package com.syuuk.patentflow.patent.dto;

public enum Recommendation {
    MAINTAIN,
    REVIEW_AGAIN,
    ABANDON,
    // AI 권고 '조건부 유지' 전용. (구 식별자 HOLD)
    CONDITIONAL_MAINTAIN
}
