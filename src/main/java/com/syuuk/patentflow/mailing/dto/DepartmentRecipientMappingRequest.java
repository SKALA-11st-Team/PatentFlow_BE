package com.syuuk.patentflow.mailing.dto;

// 부서명 변경만 허용 — 수신자 이메일은 users 테이블에서 파생되므로 여기서 관리하지 않는다.
public record DepartmentRecipientMappingRequest(String departmentName) {}
