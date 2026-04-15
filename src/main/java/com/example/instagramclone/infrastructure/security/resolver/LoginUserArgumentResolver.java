package com.example.instagramclone.infrastructure.security.resolver;

import com.example.instagramclone.core.exception.MemberErrorCode;
import com.example.instagramclone.core.exception.MemberException;
import com.example.instagramclone.infrastructure.security.annotation.LoginUser;
import com.example.instagramclone.infrastructure.security.dto.LoginUserInfoDto;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @LoginUser 어노테이션이 붙은 파라미터를 가로채서 인증 정보를 주입하는 해결사
 */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        // 지원하는 파라미터인지 확인: @LoginUser 어노테이션이 붙어있고, 타입이 LoginUserInfoDto 인 경우
        boolean hasAnnotation = parameter.hasParameterAnnotation(LoginUser.class);
        boolean hasLoginUserType = LoginUserInfoDto.class.isAssignableFrom(parameter.getParameterType());
        return hasAnnotation && hasLoginUserType;
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) {

        // SecurityContextHolder에서 인증 정보를 꺼낸다
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 인증 정보가 없거나 비로그인 상태인 경우 즉시 예외 발생 (Fail-Fast)
        if (authentication == null
                || authentication.getPrincipal().equals("anonymousUser")
                || !(authentication.getPrincipal() instanceof LoginUserInfoDto)) {
            throw new MemberException(MemberErrorCode.UNAUTHORIZED_ACCESS);
        }

        // JwtAuthenticationFilter에서 저장한 LoginUserInfoDto 객체를 바로 반환
        return authentication.getPrincipal();
    }
}
