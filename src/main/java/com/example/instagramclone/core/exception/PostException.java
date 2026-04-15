package com.example.instagramclone.core.exception;

import lombok.Getter;

@Getter
public class PostException extends BusinessException {

    public PostException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PostException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
