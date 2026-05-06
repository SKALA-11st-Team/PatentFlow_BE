package com.syuuk.patentflow.business.dto;

import java.util.List;

public record BusinessChecklistItemResponse(
        String id,
        String category,
        String title,
        String description,
        List<BusinessChecklistScoreOptionResponse> options
) {
}
