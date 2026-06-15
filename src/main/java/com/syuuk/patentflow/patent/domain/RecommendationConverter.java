package com.syuuk.patentflow.patent.domain;

import com.syuuk.patentflow.patent.dto.Recommendation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * DB에 저장된 레거시 enum 값("HOLD" 등)을 현재 Recommendation enum으로 안전하게 변환한다.
 * @Enumerated(EnumType.STRING)의 기본 valueOf() 방식은 DB에 알 수 없는 값이 있으면 예외를 던지므로
 * 이 컨버터로 교체해 알 수 없는 값을 REVIEW_AGAIN으로 폴백한다.
 *
 * 레거시 매핑:
 *   "HOLD" → CONDITIONAL_MAINTAIN (구 식별자, CONDITIONAL_MAINTAIN으로 리네임됨)
 */
@Converter
public class RecommendationConverter implements AttributeConverter<Recommendation, String> {

    @Override
    public String convertToDatabaseColumn(Recommendation attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public Recommendation convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return switch (dbData) {
            case "MAINTAIN" -> Recommendation.MAINTAIN;
            case "ABANDON" -> Recommendation.ABANDON;
            case "CONDITIONAL_MAINTAIN" -> Recommendation.CONDITIONAL_MAINTAIN;
            case "REVIEW_AGAIN" -> Recommendation.REVIEW_AGAIN;
            // 레거시 식별자: HOLD → CONDITIONAL_MAINTAIN
            case "HOLD" -> Recommendation.CONDITIONAL_MAINTAIN;
            default -> Recommendation.REVIEW_AGAIN;
        };
    }
}
