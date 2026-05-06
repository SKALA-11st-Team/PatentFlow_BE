package com.syuuk.patentflow.common.error;

public class PatentFlowException extends RuntimeException {

    private final ErrorCode errorCode;

    public PatentFlowException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
