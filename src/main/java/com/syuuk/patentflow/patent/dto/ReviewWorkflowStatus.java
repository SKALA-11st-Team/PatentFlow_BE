package com.syuuk.patentflow.patent.dto;

public enum ReviewWorkflowStatus {
    NOT_IN_REVIEW_QUARTER,
    REVIEW_QUARTER_STARTED,
    MAIL_READY,
    WAITING_BUSINESS_RESPONSE,
    BUSINESS_RESPONSE_RECEIVED,
    LEGAL_ACTION_RECORDED
}
