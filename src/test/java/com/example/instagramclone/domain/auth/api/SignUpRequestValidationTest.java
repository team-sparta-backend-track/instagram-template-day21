package com.example.instagramclone.domain.auth.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SignUpRequest Bean Validation 단위 테스트
 *
 * [목적]
 * @Valid 애노테이션의 검증 규칙(패턴, NotBlank)이 의도대로 동작하는지 검증합니다.
 * Spring Context 없이 Jakarta Validator를 직접 사용하므로 매우 빠릅니다.
 * 정규식이 변경되거나 제약조건이 삭제/수정될 경우 즉시 탐지합니다.
 */
class SignUpRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Set<ConstraintViolation<SignUpRequest>> validate(
            String username, String password, String emailOrPhone, String name) {
        SignUpRequest request = SignUpRequest.builder()
                .username(username)
                .password(password)
                .emailOrPhone(emailOrPhone)
                .name(name)
                .build();
        return validator.validate(request);
    }

    @Nested
    @DisplayName("username 필드")
    class UsernameValidation {

        @ParameterizedTest(name = "유효한 username: \"{0}\"")
        @ValueSource(strings = {"abcd", "user1", "user_name", "user.name", "user123456789012345"})
        @DisplayName("유효한 username 패턴 - 4~20자, 소문자·숫자·점·밑줄")
        void username_valid(String validUsername) {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate(validUsername, "Valid1@pass", "test@test.com", "홍길동");

            assertThat(violations).filteredOn(v -> v.getPropertyPath().toString().equals("username"))
                    .isEmpty();
        }

        @Test
        @DisplayName("실패 - username이 3자(최소 4자 미만)")
        void username_too_short() {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate("abc", "Valid1@pass", "test@test.com", "홍길동");

            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("username");
        }

        @Test
        @DisplayName("실패 - username이 21자(최대 20자 초과)")
        void username_too_long() {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate("abcdefghij12345678901", "Valid1@pass", "test@test.com", "홍길동");

            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("username");
        }

        @ParameterizedTest(name = "유효하지 않은 username: \"{0}\"")
        @ValueSource(strings = {"UpperCase", "한글이름", "user name", "user@name", "user!", "USER123"})
        @DisplayName("실패 - 허용되지 않는 문자 포함(대문자, 공백, 한글, 특수문자)")
        void username_invalid_chars(String invalidUsername) {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate(invalidUsername, "Valid1@pass", "test@test.com", "홍길동");

            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("username");
        }
    }

    @Nested
    @DisplayName("password 필드")
    class PasswordValidation {

        @ParameterizedTest(name = "유효한 password: \"{0}\"")
        @ValueSource(strings = {"Abcdef1@", "Pass1234!", "MyP@ss99", "C0mplex!Pass"})
        @DisplayName("유효한 password - 8자 이상, 영문+숫자+특수문자 포함")
        void password_valid(String validPassword) {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate("valid_user", validPassword, "test@test.com", "홍길동");

            assertThat(violations).filteredOn(v -> v.getPropertyPath().toString().equals("password"))
                    .isEmpty();
        }

        @Test
        @DisplayName("실패 - 7자(최소 8자 미만)")
        void password_too_short() {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate("valid_user", "Ab1@abc", "test@test.com", "홍길동");

            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("password");
        }

        @Test
        @DisplayName("실패 - 특수문자 없음")
        void password_missing_special_char() {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate("valid_user", "Abcdef123", "test@test.com", "홍길동");

            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("password");
        }

        @Test
        @DisplayName("실패 - 숫자 없음")
        void password_missing_digit() {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate("valid_user", "Abcdef@!", "test@test.com", "홍길동");

            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("password");
        }

        @Test
        @DisplayName("실패 - 영문 없음")
        void password_missing_letter() {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate("valid_user", "12345678@!", "test@test.com", "홍길동");

            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("password");
        }
    }

    @Nested
    @DisplayName("emailOrPhone 필드")
    class EmailOrPhoneValidation {

        @Test
        @DisplayName("실패 - 빈 문자열")
        void emailOrPhone_blank_fails() {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate("valid_user", "Valid1@pass", "", "홍길동");

            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("emailOrPhone");
        }

        @Test
        @DisplayName("실패 - null")
        void emailOrPhone_null_fails() {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate("valid_user", "Valid1@pass", null, "홍길동");

            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("emailOrPhone");
        }
    }

    @Nested
    @DisplayName("name 필드")
    class NameValidation {

        @Test
        @DisplayName("실패 - 빈 문자열")
        void name_blank_fails() {
            Set<ConstraintViolation<SignUpRequest>> violations =
                    validate("valid_user", "Valid1@pass", "test@test.com", "");

            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("name");
        }
    }

    @Test
    @DisplayName("모든 필드가 유효하면 위반 사항이 없다")
    void all_valid_fields_produce_no_violations() {
        Set<ConstraintViolation<SignUpRequest>> violations =
                validate("valid_user", "Valid1@pass", "user@example.com", "홍길동");

        assertThat(violations).isEmpty();
    }
}
