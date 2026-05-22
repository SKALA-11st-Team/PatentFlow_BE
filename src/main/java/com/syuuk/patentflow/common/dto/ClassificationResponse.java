package com.syuuk.patentflow.common.dto;

import java.util.List;

public record ClassificationResponse(
        String type,
        List<String> values
) {
}
