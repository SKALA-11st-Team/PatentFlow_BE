package com.syuuk.patentflow.patent.client;

public class PatentLookupException extends RuntimeException {

    private final String source;

    public PatentLookupException(String source, String message) {
        super(message);
        this.source = source;
    }

    public PatentLookupException(String source, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    public String source() {
        return source;
    }
}
