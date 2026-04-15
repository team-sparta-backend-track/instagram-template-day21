package com.example.instagramclone.core.exception;

import lombok.Getter;

@Getter
public class CommentException extends BusinessException {

    public CommentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CommentException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
