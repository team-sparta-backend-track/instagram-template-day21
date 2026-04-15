package com.example.instagramclone.domain.member.domain;

import com.example.instagramclone.core.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// @AllArgsConstructor 지양: 필드 순서 변경 시 위험
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(length = 100) // OAuth2 로그인 시 비밀번호가 없을 수 있으므로 nullable = true (기본값)
    private String password;

    /*
    Instagram 특성상 이메일 가입 또는 전화번호 가입이 가능하므로 Nullable
    대신 "둘 중 하나는 반드시 존재해야 한다"는 검증 로직은
    DB 레벨이 아닌, 잠시 후 구현할 Service 레벨
    (signUp 메서드)에서 수행
    */
    @Column(unique = true, length = 100)
    private String email;

    @Column(unique = true, length = 20) // Nullable (이메일로 가입한 경우)
    private String phone;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING) // Enum은 반드시 STRING으로! (ORDINAL 금지: 순서 바뀌면 DB 꼬임)
    @Column(nullable = false)
    private MemberRole role;

    @Builder
    private Member(String username, String password, String email, String phone, String name, String profileImageUrl) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.phone = phone;
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.role = MemberRole.USER; // 기본값 강제 세팅: 가입 시엔 무조건 USER
    }

    // --- 비즈니스 로직 (도메인 메서드) ---
    // Setter를 무분별하게 열어두기보다, 목적이 명확한 비즈니스 메서드를 제공하는 것이 좋다. (DDD)

    public void updateProfile(String name, String profileImageUrl) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
    }
}