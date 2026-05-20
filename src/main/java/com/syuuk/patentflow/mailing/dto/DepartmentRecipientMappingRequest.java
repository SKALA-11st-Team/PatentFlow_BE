package com.syuuk.patentflow.mailing.dto;

import java.util.List;

public record DepartmentRecipientMappingRequest(
        String departmentName,
        String managerEmail,
        String managerName,
        List<String> ccEmails
) {
}
