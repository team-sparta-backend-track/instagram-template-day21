package com.example.instagramclone.infrastructure.security.jwt;

import com.example.instagramclone.core.common.dto.ApiResponse;
import com.example.instagramclone.core.exception.ErrorResponse;
import com.example.instagramclone.core.exception.MemberErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * [필터 단의 예외 해결사, JwtAuthenticationEntryPoint]
 *
 * 스프링 시큐리티 필터 체인에서 인증 예외(401)가 발생했을 때 호출됩니다.
 * JWT 만료, 잘못된 서명 등 필터에서 발생한 구체적인 예외 상황을
 * ApiResponse 포맷의 JSON으로 클라이언트에 친절하게 응답합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        // 필터(JwtAuthenticationFilter)에서 저장한 예외 정보를 꺼내옵니다.
        Object exception = request.getAttribute("exception");

        if (exception instanceof ExpiredJwtException) {
            setResponse(request, response, MemberErrorCode.EXPIRED_TOKEN);
            return;
        }

        if (exception != null) {
            setResponse(request, response, MemberErrorCode.INVALID_TOKEN);
            return;
        }

        // 그 외 인증이 아예 없는 경우
        setResponse(request, response, MemberErrorCode.UNAUTHORIZED_ACCESS);
    }

    /**
     * ApiResponse 포맷에 맞춰 JSON 응답을 직접 생성합니다.
     */
    private void setResponse(HttpServletRequest request, HttpServletResponse response, MemberErrorCode errorCode) throws IOException {
        log.warn("[인증 에러] 코드: {}, 메시지: {}, 경로: {}", errorCode.getCode(), errorCode.getMessage(), request.getRequestURI());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(errorCode.getStatus().value());

        ApiResponse<Void> apiResponse = ApiResponse.fail(ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(errorCode.getStatus().value())
            .error(errorCode.getStatus().name())
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .path(request.getRequestURI())
            .build()
        );
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
