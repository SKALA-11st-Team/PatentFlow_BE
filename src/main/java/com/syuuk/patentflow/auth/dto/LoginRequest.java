package com.syuuk.patentflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 로그인 요청 DTO — username 대신 email 사용, 이메일 형식 검증(@Email) 추가
public record LoginRequest(
        @NotBlank @Email String email,  // 로그인 ID = 이메일 주소
        @NotBlank String password
) {
}
