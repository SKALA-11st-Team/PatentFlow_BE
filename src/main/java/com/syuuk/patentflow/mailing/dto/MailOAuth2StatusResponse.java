package com.syuuk.patentflow.mailing.dto;

public record MailOAuth2StatusResponse(
        boolean connected,
        String connectedEmail // 연동된 Gmail 주소. connected=false 이면 null
) {}
