package com.accountposting.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }
}
