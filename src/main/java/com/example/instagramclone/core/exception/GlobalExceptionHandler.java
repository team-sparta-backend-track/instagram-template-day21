package com.example.instagramclone.core.exception;

import com.example.instagramclone.core.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

/**
 * 전역 예외 처리기 (Global Exception Handler)
 * 컨트롤러 전역에서 발생하는 예외를 감지하고, 적절한 ErrorResponse를 반환하는 역할을 합니다.
 * @RestControllerAdvice: 모든 @RestController에서 발생한 예외를 잡아서 처리합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 실행 중 발생하는 예외(MemberException, PostException 등)를 처리합니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("BusinessException : {}", e.getMessage()); // 서버 로그에 에러 기록
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus()) // ErrorCode에 정의된 HTTP 상태 코드 사용
                .body(ApiResponse.fail(buildErrorResponse(errorCode, e.getMessage(), request.getRequestURI())));
    }

    /**
     * @Valid 어노테이션을 통한 입력값 검증 실패 시 발생하는 예외를 처리합니다.
     * 예: 비밀번호가 정규표현식에 맞지 않거나, 필수값이 누락된 경우
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.warn("ValidationException : {}", e.getMessage());
        // 검증 실패 메시지 중 첫 번째 메시지를 가져옵니다.
        String errorMessage = e.getBindingResult().getFieldError().getDefaultMessage();
        return ResponseEntity
                .badRequest() // 400 Bad Request
                .body(ApiResponse.fail(buildErrorResponse(CommonErrorCode.INVALID_INPUT_VALUE, errorMessage, request.getRequestURI())));
    }

    /**
     * 파일 업로드 용량 제한을 초과했을 때 발생하는 예외를 처리합니다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(Exception e, HttpServletRequest request) {
        log.warn("MaxUploadSizeExceededException : {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail(buildErrorResponse(CommonErrorCode.INVALID_INPUT_VALUE, "파일 업로드 용량이 초과되었습니다.", request.getRequestURI())));
    }

    /**
     * 정적 리소스를 찾을 수 없을 때 발생하는 예외 (404 Not Found)
     * 파비콘이나 정적 파일 요청이 실패할 때 (예: .well-known/appspecific/com.chrome.devtools.json) 에러 로그를 남기지 않고 무시합니다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        log.debug("NoResourceFoundException : {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(buildErrorResponse(CommonErrorCode.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.", request.getRequestURI())));
    }

    /**
     * [Day 18] 분산 락(또는 DB 비관적 락) 획득 실패 시 발생하는 예외를 처리합니다.
     *
     * <p>락을 얻지 못했다는 것은 "현재 같은 자원에 다른 요청이 처리 중"이라는 의미이므로
     * {@code 409 Conflict}를 반환합니다.
     * 클라이언트는 이 응답을 받으면 잠시 후 재시도하도록 안내받습니다.
     */
    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleLockAcquisitionException(
            LockAcquisitionException e, HttpServletRequest request) {
        log.warn("LockAcquisitionException : {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT) // 409 Conflict
                .body(ApiResponse.fail(buildErrorResponse(
                        CommonErrorCode.CONFLICT, e.getMessage(), request.getRequestURI())));
    }

    /**
     * 위에서 처리하지 못한 나머지 모든 예외(Exception)를 처리합니다.
     * 예상치 못한 서버 에러(NullPointerException 등)가 여기에 해당합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("Exception : {}", e);
        // 보안을 위해 내부 에러 메시지는 숨기고, "서버 내부 오류"라는 일반적인 메시지를 반환합니다.

        return ResponseEntity
                .internalServerError() // 500 Internal Server Error
                .body(ApiResponse.fail(buildErrorResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, "알 수 없는 에러가 발생했습니다.", request.getRequestURI())));
    }

    /**
     * ErrorResponse 객체를 생성하는 유틸리티 메서드입니다.
     */
    private ErrorResponse buildErrorResponse(ErrorCode errorCode, String message, String path) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().name())
                .code(errorCode.getCode())
                .message(message)
                .path(path)
                .build();
    }
}
