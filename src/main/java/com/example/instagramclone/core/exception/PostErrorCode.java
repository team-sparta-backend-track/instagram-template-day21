package com.example.instagramclone.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PostErrorCode implements ErrorCode {
    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "P001", "허용되지 않는 파일 확장자이거나 MIME 타입입니다."),
    FILE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "P002", "파일 업로드 중 오류가 발생했습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "P003", "게시물을 찾을 수 없습니다.")
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
