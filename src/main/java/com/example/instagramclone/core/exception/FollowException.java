package com.example.instagramclone.core.exception;

import lombok.Getter;

@Getter
public class FollowException extends BusinessException {

    public FollowException(ErrorCode errorCode) {
        super(errorCode);
    }
}
