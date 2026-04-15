package com.example.instagramclone.domain.member.domain;

import com.example.instagramclone.domain.member.infrastructure.MemberRepositoryCustomImpl;
import com.example.instagramclone.domain.post.infrastructure.PostGridQueryHelper;
import com.example.instagramclone.infrastructure.persistence.QueryDslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MemberRepository 통합 테스트 (@DataJpaTest - H2 인메모리 DB)
 *
 * [테스트 범위]
 * - 저장(save): 이메일/전화번호 가입, role 기본값
 * - existsBy*: username, email, phone 존재/미존재 6가지
 * - findBy*: username, email, phone 조회 성공/실패 6가지
 * - 고유 제약조건: username, email, phone 중복 저장 시 예외
 * - 비즈니스 메서드 영속화: updateProfile() 변경 후 flush/reload 검증
 * - searchByUsername(): QueryDSL 커스텀 쿼리 — 부분 일치, 대소문자 무시
 *
 */
@DataJpaTest
@Import({QueryDslConfig.class, PostGridQueryHelper.class})
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestEntityManager entityManager;

    // ============================================================
    // 저장(save)
    // ============================================================

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("이메일 가입 - ID 생성, role은 USER로 강제, phone은 null")
        void save_with_email_sets_defaults() {
            Member member = Member.builder()
                    .username("hongtutor")
                    .password("encoded1234")
                    .email("hong@test.com")
                    .name("Hong Gill Dong")
                    .build();

            Member saved = memberRepository.save(member);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUsername()).isEqualTo("hongtutor");
            assertThat(saved.getEmail()).isEqualTo("hong@test.com");
            assertThat(saved.getPhone()).isNull();
            assertThat(saved.getRole()).isEqualTo(MemberRole.USER);
        }

        @Test
        @DisplayName("전화번호 가입 - ID 생성, role은 USER로 강제, email은 null")
        void save_with_phone_sets_defaults() {
            Member member = Member.builder()
                    .username("phoneuser")
                    .password("encoded1234")
                    .phone("01012345678")
                    .name("Phone User")
                    .build();

            Member saved = memberRepository.save(member);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getPhone()).isEqualTo("01012345678");
            assertThat(saved.getEmail()).isNull();
            assertThat(saved.getRole()).isEqualTo(MemberRole.USER);
        }

    }

    // ============================================================
    // existsBy*
    // ============================================================

    @Nested
    @DisplayName("existsBy*()")
    class ExistsBy {

        @BeforeEach
        void setUp() {
            memberRepository.save(Member.builder()
                    .username("emailuser")
                    .password("pw")
                    .email("exist@test.com")
                    .name("Email User")
                    .build());

            memberRepository.save(Member.builder()
                    .username("phoneuser")
                    .password("pw")
                    .phone("01011112222")
                    .name("Phone User")
                    .build());
        }

        @Test
        @DisplayName("existsByEmail - 저장된 이메일은 true 반환")
        void existsByEmail_returns_true_when_exists() {
            assertThat(memberRepository.existsByEmail("exist@test.com")).isTrue();
        }

        @Test
        @DisplayName("existsByEmail - 미존재 이메일은 false 반환")
        void existsByEmail_returns_false_when_not_exists() {
            assertThat(memberRepository.existsByEmail("none@test.com")).isFalse();
        }

        @Test
        @DisplayName("existsByUsername - 저장된 username은 true 반환")
        void existsByUsername_returns_true_when_exists() {
            assertThat(memberRepository.existsByUsername("emailuser")).isTrue();
        }

        @Test
        @DisplayName("existsByUsername - 미존재 username은 false 반환")
        void existsByUsername_returns_false_when_not_exists() {
            assertThat(memberRepository.existsByUsername("ghost_user")).isFalse();
        }

        @Test
        @DisplayName("existsByPhone - 저장된 전화번호는 true 반환")
        void existsByPhone_returns_true_when_exists() {
            assertThat(memberRepository.existsByPhone("01011112222")).isTrue();
        }

        @Test
        @DisplayName("existsByPhone - 미존재 전화번호는 false 반환")
        void existsByPhone_returns_false_when_not_exists() {
            assertThat(memberRepository.existsByPhone("01099999999")).isFalse();
        }
    }

    // ============================================================
    // findBy*
    // ============================================================

    @Nested
    @DisplayName("findBy*()")
    class FindBy {

        @BeforeEach
        void setUp() {
            memberRepository.save(Member.builder()
                    .username("finduser")
                    .password("pw")
                    .email("find@test.com")
                    .name("Find User")
                    .build());

            memberRepository.save(Member.builder()
                    .username("phoneuser")
                    .password("pw")
                    .phone("01033334444")
                    .name("Phone User")
                    .build());
        }

        @Test
        @DisplayName("findByEmail - 존재하는 이메일로 조회 시 Member 반환")
        void findByEmail_returns_member_when_exists() {
            Optional<Member> result = memberRepository.findByEmail("find@test.com");

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("finduser");
        }

        @Test
        @DisplayName("findByEmail - 미존재 이메일로 조회 시 empty 반환")
        void findByEmail_returns_empty_when_not_exists() {
            Optional<Member> result = memberRepository.findByEmail("nobody@test.com");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findByUsername - 존재하는 username으로 조회 시 Member 반환")
        void findByUsername_returns_member_when_exists() {
            Optional<Member> result = memberRepository.findByUsername("finduser");

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("find@test.com");
        }

        @Test
        @DisplayName("findByUsername - 미존재 username으로 조회 시 empty 반환")
        void findByUsername_returns_empty_when_not_exists() {
            Optional<Member> result = memberRepository.findByUsername("ghost_user");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findByPhone - 존재하는 전화번호로 조회 시 Member 반환")
        void findByPhone_returns_member_when_exists() {
            Optional<Member> result = memberRepository.findByPhone("01033334444");

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("phoneuser");
        }

        @Test
        @DisplayName("findByPhone - 미존재 전화번호로 조회 시 empty 반환")
        void findByPhone_returns_empty_when_not_exists() {
            Optional<Member> result = memberRepository.findByPhone("01000000000");

            assertThat(result).isEmpty();
        }
    }

    // ============================================================
    // 고유 제약조건 (Unique Constraint)
    // ============================================================

    @Nested
    @DisplayName("고유 제약조건 위반")
    class UniqueConstraint {

        @Test
        @DisplayName("중복 이메일 저장 시 DataIntegrityViolationException 발생")
        void duplicate_email_throws_exception() {
            memberRepository.save(Member.builder()
                    .username("user1")
                    .password("pw")
                    .email("same@test.com")
                    .name("User1")
                    .build());

            assertThatThrownBy(() -> memberRepository.saveAndFlush(
                    Member.builder()
                            .username("user2")
                            .password("pw")
                            .email("same@test.com")
                            .name("User2")
                            .build()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("중복 username 저장 시 DataIntegrityViolationException 발생")
        void duplicate_username_throws_exception() {
            memberRepository.save(Member.builder()
                    .username("same_user")
                    .password("pw")
                    .email("user1@test.com")
                    .name("User1")
                    .build());

            assertThatThrownBy(() -> memberRepository.saveAndFlush(
                    Member.builder()
                            .username("same_user")
                            .password("pw")
                            .email("user2@test.com")
                            .name("User2")
                            .build()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("중복 전화번호 저장 시 DataIntegrityViolationException 발생")
        void duplicate_phone_throws_exception() {
            memberRepository.save(Member.builder()
                    .username("phoneuser1")
                    .password("pw")
                    .phone("01055556666")
                    .name("PhoneUser1")
                    .build());

            assertThatThrownBy(() -> memberRepository.saveAndFlush(
                    Member.builder()
                            .username("phoneuser2")
                            .password("pw")
                            .phone("01055556666")
                            .name("PhoneUser2")
                            .build()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ============================================================
    // 비즈니스 메서드 영속화
    // ============================================================

    @Nested
    @DisplayName("비즈니스 메서드 영속화")
    class BusinessMethodPersistence {

        @Test
        @DisplayName("updateProfile() 호출 후 flush/reload 시 변경 내용이 DB에 반영된다")
        void updateProfile_persists_changes() {
            Member member = memberRepository.save(Member.builder()
                    .username("profileuser")
                    .password("pw")
                    .email("profile@test.com")
                    .name("Original Name")
                    .build());

            member.updateProfile("Updated Name", "https://cdn.example.com/new.jpg");
            entityManager.flush();
            entityManager.clear();

            Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("Updated Name");
            assertThat(reloaded.getProfileImageUrl()).isEqualTo("https://cdn.example.com/new.jpg");
        }

        @Test
        @DisplayName("updateProfile() 호출 시 profileImageUrl을 null로 초기화할 수 있다")
        void updateProfile_can_clear_profileImageUrl() {
            Member member = memberRepository.save(Member.builder()
                    .username("imguser")
                    .password("pw")
                    .email("img@test.com")
                    .name("Image User")
                    .build());

            member.updateProfile("Image User", null);
            entityManager.flush();
            entityManager.clear();

            Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
            assertThat(reloaded.getProfileImageUrl()).isNull();
        }
    }

    // ============================================================
    // QueryDSL 커스텀 쿼리: searchByUsername()
    // ============================================================

    @Nested
    @DisplayName("searchByUsername() - QueryDSL 커스텀 쿼리")
    class SearchByUsername {

        @BeforeEach
        void setUp() {
            memberRepository.save(Member.builder()
                    .username("john_doe").password("pw").email("john@test.com").name("John").build());
            memberRepository.save(Member.builder()
                    .username("jane_smith").password("pw").email("jane@test.com").name("Jane").build());
            memberRepository.save(Member.builder()
                    .username("bob_jones").password("pw").email("bob@test.com").name("Bob").build());
        }

        @Test
        @DisplayName("keyword가 포함된 username을 가진 회원만 반환한다")
        void returns_members_whose_username_contains_keyword() {
            List<Member> result = memberRepository.searchByUsername("john");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUsername()).isEqualTo("john_doe");
        }

        @Test
        @DisplayName("대소문자를 구분하지 않고 검색한다 (JOHN → john_doe 반환)")
        void search_is_case_insensitive() {
            List<Member> result = memberRepository.searchByUsername("JOHN");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUsername()).isEqualTo("john_doe");
        }

        @Test
        @DisplayName("여러 username에 keyword가 포함되면 모두 반환한다")
        void returns_multiple_matches() {
            // "john_doe", "jane_smith", "bob_jones" 모두 'j' 또는 'o' 포함
            List<Member> result = memberRepository.searchByUsername("o");

            // john_doe(o 포함), bob_jones(o 포함) → 2건
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Member::getUsername)
                    .containsExactlyInAnyOrder("john_doe", "bob_jones");
        }

        @Test
        @DisplayName("일치하는 username이 없으면 빈 리스트를 반환한다")
        void returns_empty_list_when_no_match() {
            List<Member> result = memberRepository.searchByUsername("zzz_no_match");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("빈 keyword로 검색하면 모든 회원이 반환된다")
        void empty_keyword_returns_all_members() {
            List<Member> result = memberRepository.searchByUsername("");

            assertThat(result).hasSize(3);
        }
    }
}
