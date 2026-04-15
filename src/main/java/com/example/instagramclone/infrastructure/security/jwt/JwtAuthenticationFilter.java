package com.example.instagramclone.infrastructure.security.jwt;

import com.example.instagramclone.core.constant.AuthConstants;
import com.example.instagramclone.infrastructure.security.dto.LoginUserInfoDto;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * [모든 요청의 첫 번째 검문소, JwtAuthenticationFilter]
 *
 * Q. 왜 스프링의 Interceptor가 아니라 서블릿의 Filter를 사용할까요?
 * A. 방어의 "최전선"이기 때문입니다.
 *    Interceptor는 스프링 MVC(DispatcherServlet) 내부로 들어온 이후에 동작합니다.
 *    악성 요청이나 미인증 요청을 스프링 내부까지 들어오게 허용하면 리소스가 낭비되고 공격 표면이 넓어집니다.
 *    따라서 "가장 바깥쪽 문"인 서블릿 Filter 단에서 원천 차단하는 것이 현업의 표준 보안 프랙티스입니다.
 *
 * Q. 왜 Filter 대신 OncePerRequestFilter를 상속받나요?
 * A. 내부 포워딩(동일 서버 내 다른 컨트롤러로 요청 전달 등)이 발생할 때
 *    일반 Filter는 불필요하게 두 번 이상 실행될 수 있습니다.
 *    OncePerRequestFilter는 한 요청(Request) 당 정확히 한 번만 검문하도록 보장합니다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Request Header 에서 클라이언트가 보낸 JWT 토큰을 가로채기
        String token = resolveToken(request);

        try {
            // 2. 가로챈 토큰이 존재하고(null이 아니고), 위변조 및 만료되지 않은 "유효한" 토큰인지 검사
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {

                // 3. 유효한 토큰이면 Spring Security Context 에 인증 정보 심기
                Long memberId = jwtTokenProvider.getMemberId(token);
                String role = jwtTokenProvider.getRole(token);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        LoginUserInfoDto.builder().id(memberId).build(),
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(StringUtils.hasText(role) ? role : "ROLE_USER"))
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Security Context에 '{}' 인증 정보를 저장했습니다.", memberId);
            }
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다: {}", e.getMessage());
            request.setAttribute("exception", e);
        } catch (SecurityException | MalformedJwtException | SignatureException e) {
            log.warn("잘못된 JWT 서명입니다: {}", e.getMessage());
            request.setAttribute("exception", e);
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰입니다: {}", e.getMessage());
            request.setAttribute("exception", e);
        } catch (Exception e) {
            log.error("JWT 검증 중 알 수 없는 예외가 발생했습니다: {}", e.getMessage());
            request.setAttribute("exception", e);
        }

        // 4. 다음 검문소(필터)로 요청 넘기기
        // 주의: 토큰이 없거나 유효하지 않아도 여기서 에러를 내지 않고 일단 넘깁니다.
        // 인증정보(도장)가 없는 상태로 넘어가면, 이어지는 인가(Authorization) 필터가 알아서 401(Unauthorized)로 튕겨냅니다.
        filterChain.doFilter(request, response);
    }

    /**
     * HTTP Header 에서 토큰 값만 순수하게 추출하는 헬퍼 메서드
     * 보통 클라이언트는 "Authorization: Bearer eyJhbGci..." 형태로 토큰을 보냅니다.
     */
    private String resolveToken(HttpServletRequest request) {
        // "Authorization" 헤더 값을 가져옵니다.
        String bearerToken = request.getHeader(AuthConstants.AUTHORIZATION_HEADER);

        // 가져온 값이 "Bearer " 로 시작한다면, 앞의 7글자("Bearer ")를 잘라내고 순수 토큰 문자열만 반환합니다.
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(AuthConstants.BEARER_PREFIX)) {
            return bearerToken.substring(AuthConstants.BEARER_PREFIX.length()); // "Bearer " 길이만큼 잘라냄
        }

        return null; // 토큰이 없거나 규격에 맞지 않으면 null 반환
    }
}
