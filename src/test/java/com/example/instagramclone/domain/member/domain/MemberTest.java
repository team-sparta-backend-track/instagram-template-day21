package com.example.instagramclone.domain.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Member 엔티티 단위 테스트
 *
 * [목적]
 * Spring Context 없이 순수 Java로 Member 엔티티의 빌더와 비즈니스 메서드를 검증합니다.
 *
 * [보호해야 할 불변 규칙]
 * 1. role은 외부에서 설정할 수 없고, 빌더 내부에서 항상 MemberRole.USER로 강제된다.
 * 2. updateProfile()은 name, profileImageUrl만 변경하며 다른 핵심 필드는 불변이다.
 */
class MemberTest {

    // ============================================================
    // Builder
    // ============================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("이메일로 생성 시 email 필드 설정, phone은 null")
        void builder_with_email() {
            Member member = Member.builder()
                    .username("testuser")
                    .password("encoded_pw")
                    .email("test@example.com")
                    .name("테스터")
                    .build();

            assertThat(member.getUsername()).isEqualTo("testuser");
            assertThat(member.getEmail()).isEqualTo("test@example.com");
            assertThat(member.getPhone()).isNull();
            assertThat(member.getName()).isEqualTo("테스터");
        }

        @Test
        @DisplayName("전화번호로 생성 시 phone 필드 설정, email은 null")
        void builder_with_phone() {
            Member member = Member.builder()
                    .username("phoneuser")
                    .password("encoded_pw")
                    .phone("01012345678")
                    .name("전화 유저")
                    .build();

            assertThat(member.getPhone()).isEqualTo("01012345678");
            assertThat(member.getEmail()).isNull();
        }

        @Test
        @DisplayName("role은 빌더 파라미터와 무관하게 항상 USER로 강제 설정된다 (핵심 불변 규칙)")
        void builder_always_sets_role_to_user() {
            Member member = Member.builder()
                    .username("anyuser")
                    .password("pw")
                    .email("any@test.com")
                    .name("Any")
                    .build();

            assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        }

        @Test
        @DisplayName("profileImageUrl 없이 생성 시 null (선택 필드)")
        void builder_without_profileImageUrl_is_null() {
            Member member = Member.builder()
                    .username("noimguser")
                    .password("pw")
                    .email("noimg@test.com")
                    .name("No Image")
                    .build();

            assertThat(member.getProfileImageUrl()).isNull();
        }

        @Test
        @DisplayName("profileImageUrl을 포함하여 생성 시 해당 값이 설정된다")
        void builder_with_profileImageUrl() {
            String imageUrl = "https://cdn.example.com/profile.jpg";
            Member member = Member.builder()
                    .username("imguser")
                    .password("pw")
                    .email("img@test.com")
                    .name("Image User")
                    .profileImageUrl(imageUrl)
                    .build();

            assertThat(member.getProfileImageUrl()).isEqualTo(imageUrl);
        }

        @Test
        @DisplayName("DB 저장 전 id는 null이다")
        void builder_id_is_null_before_save() {
            Member member = Member.builder()
                    .username("newuser")
                    .password("pw")
                    .email("new@test.com")
                    .name("New")
                    .build();

            assertThat(member.getId()).isNull();
        }
    }

    // ============================================================
    // updateProfile()
    // ============================================================

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfile {

        @Test
        @DisplayName("name과 profileImageUrl이 새 값으로 변경된다")
        void updateProfile_changes_name_and_imageUrl() {
            Member member = Member.builder()
                    .username("updateuser")
                    .password("pw")
                    .email("update@test.com")
                    .name("Original Name")
                    .build();

            member.updateProfile("Updated Name", "https://cdn.example.com/new.jpg");

            assertThat(member.getName()).isEqualTo("Updated Name");
            assertThat(member.getProfileImageUrl()).isEqualTo("https://cdn.example.com/new.jpg");
        }

        @Test
        @DisplayName("updateProfile() 호출 후 username, email, password, role은 변경되지 않는다 (불변 필드 보호)")
        void updateProfile_does_not_change_immutable_fields() {
            Member member = Member.builder()
                    .username("immutable_user")
                    .password("original_pw")
                    .email("original@test.com")
                    .name("Original")
                    .build();

            member.updateProfile("New Name", "https://cdn.example.com/img.jpg");

            assertThat(member.getUsername()).isEqualTo("immutable_user");
            assertThat(member.getPassword()).isEqualTo("original_pw");
            assertThat(member.getEmail()).isEqualTo("original@test.com");
            assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        }

        @Test
        @DisplayName("profileImageUrl을 null로 업데이트할 수 있다 (이미지 삭제 시나리오)")
        void updateProfile_can_set_imageUrl_to_null() {
            Member member = Member.builder()
                    .username("imguser")
                    .password("pw")
                    .email("img@test.com")
                    .name("Image User")
                    .profileImageUrl("https://cdn.example.com/old.jpg")
                    .build();

            member.updateProfile("Image User", null);

            assertThat(member.getProfileImageUrl()).isNull();
        }

        @Test
        @DisplayName("연속 호출 시 항상 마지막 값으로 덮어쓴다")
        void updateProfile_multiple_calls_keeps_last_value() {
            Member member = Member.builder()
                    .username("multiuser")
                    .password("pw")
                    .email("multi@test.com")
                    .name("First")
                    .build();

            member.updateProfile("Second", "https://cdn.example.com/second.jpg");
            member.updateProfile("Third", "https://cdn.example.com/third.jpg");

            assertThat(member.getName()).isEqualTo("Third");
            assertThat(member.getProfileImageUrl()).isEqualTo("https://cdn.example.com/third.jpg");
        }
    }
}
