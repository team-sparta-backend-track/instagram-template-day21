package com.example.instagramclone.core.exception;

import lombok.Getter;

@Getter
public class HashtagException extends BusinessException {

    public HashtagException(ErrorCode errorCode) {
        super(errorCode);
    }

    public HashtagException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
