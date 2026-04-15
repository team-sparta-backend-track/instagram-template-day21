package com.example.instagramclone.domain.member.application;

import com.example.instagramclone.domain.member.api.MemberProfileResponse;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceTest {

    @Mock
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberProfileService memberProfileService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // self-invocation 패턴: @Lazy @Autowired self 필드는 @InjectMocks로 주입되지 않음
        ReflectionTestUtils.setField(memberProfileService, "self", memberProfileService);
    }

    private Member buildMockMember(Long id, String username) {
        Member member = Member.builder()
                .username(username)
                .password("encoded_pw")
                .email(username + "@test.com")
                .name("테스트 유저")
                .profileImageUrl("/profiles/" + username + ".jpg")
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Nested
    @DisplayName("getProfileByUsername()")
    class GetProfileByUsername {

        @Test
        @DisplayName("성공 - 자기 자신의 프로필이면 isFollowing=false, isCurrentUser=true")
        void success_my_profile_returns_false_and_me_true() {
            Member loginMember = buildMockMember(1L, "me");
            Member me = buildMockMember(1L, "me");

            given(memberService.findByUsername("me")).willReturn(me);
            given(memberRepository.getProfileHeader(me.getId(), loginMember.getId()))
                    .willReturn(MemberProfileResponse.of(me, 10L, 20L, 30L, false, true));

            MemberProfileResponse response = memberProfileService.getProfileByUsername(loginMember.getId(), "me");

            assertThat(response.memberId()).isEqualTo(1L);
            assertThat(response.username()).isEqualTo("me");
            assertThat(response.followerCount()).isEqualTo(10L);
            assertThat(response.followingCount()).isEqualTo(20L);
            assertThat(response.postCount()).isEqualTo(30L);
            assertThat(response.isFollowing()).isFalse();
            assertThat(response.isCurrentUser()).isTrue();
        }

        @Test
        @DisplayName("성공 - 다른 유저 프로필이면 FollowService.isFollowing 결과를 응답에 반영")
        void success_other_profile_uses_follow_service() {
            Long loginMemberId = 1L;
            Member targetMember = buildMockMember(2L, "target");

            given(memberService.findByUsername("target")).willReturn(targetMember);
            given(memberRepository.getProfileHeader(targetMember.getId(), loginMemberId))
                    .willReturn(MemberProfileResponse.of(targetMember, 11L, 22L, 33L, true, false));

            MemberProfileResponse response = memberProfileService.getProfileByUsername(loginMemberId, "target");

            assertThat(response.memberId()).isEqualTo(2L);
            assertThat(response.username()).isEqualTo("target");
            assertThat(response.profileImageUrl()).isEqualTo("/profiles/target.jpg");
            assertThat(response.followerCount()).isEqualTo(11L);
            assertThat(response.followingCount()).isEqualTo(22L);
            assertThat(response.postCount()).isEqualTo(33L);
            assertThat(response.isFollowing()).isTrue();
            assertThat(response.isCurrentUser()).isFalse();
        }

        @Test
        @DisplayName("성공 - username 기반으로 프로필 헤더를 조회한다")
        void success_profile_header_by_username() {
            Long loginMemberId = 1L;
            Member targetMember = buildMockMember(2L, "target");

            given(memberService.findByUsername("target")).willReturn(targetMember);
            given(memberRepository.getProfileHeader(targetMember.getId(), loginMemberId))
                    .willReturn(MemberProfileResponse.of(targetMember, 1L, 2L, 3L, true, false));

            MemberProfileResponse response = memberProfileService.getProfileByUsername(loginMemberId, "target");

            assertThat(response.memberId()).isEqualTo(2L);
            assertThat(response.username()).isEqualTo("target");
            assertThat(response.followerCount()).isEqualTo(1L);
            assertThat(response.followingCount()).isEqualTo(2L);
            assertThat(response.postCount()).isEqualTo(3L);
            assertThat(response.isFollowing()).isTrue();
            assertThat(response.isCurrentUser()).isFalse();
        }
    }
}
