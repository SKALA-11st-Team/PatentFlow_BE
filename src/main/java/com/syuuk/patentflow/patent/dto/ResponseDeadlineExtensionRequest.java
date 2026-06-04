package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * @description 지연된 사업부 회신 요청의 연장 회신기한과 대상 분기 이력을 전달한다.
 */
public record ResponseDeadlineExtensionRequest(
        @NotEmpty List<Target> targets,
        @NotNull LocalDate responseDueDate
) {
    /**
     * @description 특정 특허의 특정 분기 review history를 지정한다.
     */
    public record Target(
            String patentId,
            String quarterKey
    ) {
    }
}
