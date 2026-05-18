package com.syuuk.patentflow.mailing.dto;

import java.util.List;

public record DepartmentRecipientMappingResponse(
        String departmentId,
        String departmentName,
        String managerEmail,
        String managerName,
        List<String> ccEmails,
        String updatedAt
) {
}
