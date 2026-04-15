package com.example.instagramclone.infrastructure.security.config;

import com.example.instagramclone.infrastructure.security.jwt.JwtAuthenticationEntryPoint;
import com.example.instagramclone.infrastructure.security.jwt.JwtAuthenticationFilter;
import com.example.instagramclone.infrastructure.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable()) // CSRF 보호 비활성화 (JWT를 사용하므로 불필요)
                .formLogin(form -> form.disable()) // 기본 폼 로그인 비활성화
                .httpBasic(basic -> basic.disable()) // 기본 HTTP Basic 인증 비활성화
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(jwtAuthenticationEntryPoint) // 필터 단 예외 처리EntryPoint 등록
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 세션을 사용하지 않음 (Stateless)
                )
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .authorizeHttpRequests(auth -> auth
                        // ==================== 개발용 허용 ====================
                        .requestMatchers(
                                "/", "/assets/**", "/img/**", "/error", "/favicon.ico",
                                "/h2-console/**",
                                "/actuator/**",
                                "/api/benchmark/offset-vs-cursor"
                        ).permitAll()

                        // ==================== 인증 없이 허용 ====================

                        // Step 3: "우리 서비스의 API 출입 통제소 (authorizeHttpRequests)"
                        // Q. 왜 permitAll()을 쓰나요?
                        // A. 인스타그램은 로그인하지 않아도 회원가입과 로그인은 할 수 있어야 합니다.
                        //    이메일 중복 검사 등 인증이 필요 없는 최소한의 API만 문을 열어둡니다.
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/signup",
                                "/api/auth/reissue"          // Refresh Token 재발급
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/auth/check-duplicate"
                        ).permitAll()

                        // [실무 꿀팁] 와일드카드("/api/auth/**") 주의보!
                        // 앞선 API들 외에 /api/auth/logout 도 같은 폴더(?)에 있지만,
                        // 로그아웃은 반드시 "로그인된(인증된)" 사람만 할 수 있어야 합니다.
                        // 따라서 로그아웃이나 다른 API들은 아래의 anyRequest().authenticated() 에 걸려서 보안을 유지하게 됩니다.

                        // 그 외의 모든 API (게시글 작성, 피드 조회, 댓글 달기 등)는
                        // "반드시 인증된(로그인된)" 사용자만 접근할 수 있도록 철저하게 막아버립니다.
                        .anyRequest().authenticated()
                )
                // Step 2: "SecurityContext 에 신분증 걸어두기" (Filter 등록)
                // 우리가 직접 만든 JwtAuthenticationFilter 객체를 생성하여 필터 체인에 끼워 넣습니다.
                // Q. 왜 UsernamePasswordAuthenticationFilter '앞(Before)'에 넣나요?
                // A. 스프링 시큐리티의 기본 인증 동작(폼 로그인 시 유저네임/비번 검사)이 일어나기 전에,
                //    우리가 가로챈 JWT 토큰이 유효하다면 "이 사람은 이미 통과!"라고 인증 도장(Authentication)을
                //    미리 쾅 찍어주기 위해서입니다.
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
