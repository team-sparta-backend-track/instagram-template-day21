package com.example.instagramclone.infrastructure.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String ISSUER = "InstagramCloneAuthServer"; // 표준 Claim: 발급자 (iss)

    @Value("${jwt.secret-key}")
    private String secretKeyString; // 대칭키(HS256)용 프로퍼티

    @Value("${jwt.access-token-validity-time}")
    private long accessTokenValidityInMilliseconds;

    @Value("${jwt.refresh-token-validity-time}")
    private long refreshTokenValidityInMilliseconds;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // [아키텍처 의사결정] 모놀리식 아키텍처 환경에서는 대칭키(HS256)를 사용하는 것이 복잡도 대비 가장 합당합니다.
        // MSA 환경(ES256/비대칭키)과의 Trade-off를 고려한 선택입니다.
        byte[] keyBytes = Decoders.BASE64.decode(secretKeyString);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret-key는 최소 32바이트(256bit) 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);

        log.info("HS256 대칭키(Secret Key)가 성공적으로 초기화되었습니다.");
    }

    /**
     * 회원(User)의 정보를 기반으로 Access Token을 생성합니다.
     * @param memberId 회원의 고유 식별자 (PK)
     * @param role 회원의 권한 (ROLE_USER 등)
     * @return 발급된 Access Token 문자열
     */
    public String createAccessToken(Long memberId, String role) {
        return buildToken(memberId, role, accessTokenValidityInMilliseconds);
    }

    /**
     * 회원(User)의 정보를 기반으로 Refresh Token을 생성합니다.
     * (Refresh Token은 권한 정보 없이 PK만 담는 것이 일반적입니다)
     * @param memberId 회원의 고유 식별자 (PK)
     * @return 발급된 Refresh Token 문자열
     */
    public String createRefreshToken(Long memberId) {
        return buildToken(memberId, null, refreshTokenValidityInMilliseconds);
    }

    /**
     * 실제 JWT 토큰 생성을 담당하는 내부 헬퍼 메서드
     */
    private String buildToken(Long memberId, String role, long validityTimeInMs) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityTimeInMs);

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(memberId)) // PK를 Subject로 지정
                .issuer(ISSUER)                    // 표준 Claim: 토큰 발급자 명시
                .issuedAt(now)                     // 표준 Claim: 토큰 발행 시간 (iat)
                .expiration(validity)              // 표준 Claim: 설정된 만료 시간 (exp)
                .id(UUID.randomUUID().toString())  // 표준 Claim: 토큰 고유 ID (jti)
                .signWith(key);                    // SecretKey → HS256 자동 적용 (모놀리식 대칭키)

        if (role != null) {
            builder.claim("role", role); // 부가 정보(Custom Claim) 추가
        }

        return builder.compact();
    }

    /**
     * 토큰에서 유저의 PK(Subject)를 추출합니다.
     */
    public Long getMemberId(String token) {
        String subject = parseClaims(token).getSubject();
        return Long.parseLong(subject);
    }

    /**
     * 토큰에서 유저의 권한(Role)을 추출합니다.
     */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * 토큰에서 Claim(Payload) 정보를 파싱하고 서명/만료를 검증합니다
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER) // 내가 발급한 토큰이 맞는지 확인
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 토큰의 유효성 및 만료 기간을 검사합니다.
     * 예외가 발생하면 호출한 곳(필터)에서 처리할 수 있도록 던집니다.
     */
    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }

    public int getRefreshTokenValidityInSeconds() {
        return (int) (refreshTokenValidityInMilliseconds / 1000);
    }
}
