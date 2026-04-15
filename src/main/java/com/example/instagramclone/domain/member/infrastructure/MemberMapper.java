package com.example.instagramclone.domain.member.infrastructure;

import com.example.instagramclone.core.constant.AuthConstants;
import com.example.instagramclone.domain.auth.api.AuthTokens;
import com.example.instagramclone.domain.auth.api.LoginResponse;
import com.example.instagramclone.domain.auth.api.SignUpResponse;
import com.example.instagramclone.domain.member.domain.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    /**
     * Member → LoginResponse.UserInfoDto
     * id, username, name, profileImageUrl 이름이 동일하므로 자동 매핑됩니다.
     *
     * [보안 포인트]
     * Member에는 password 필드가 있지만 UserInfoDto에는 없으므로 MapStruct가 자동으로 무시합니다.
     */
    LoginResponse.UserInfoDto toUserInfoDto(Member member);

    /**
     * Member → SignUpResponse
     *
     * SignUpResponse.message는 Member 엔티티에 없는 필드입니다.
     * @Mapping(target = "message", constant = "...")으로도 처리할 수 있지만,
     * AuthConstants 상수를 재사용해 문자열 중복을 방지하기 위해 default 메서드로 구현합니다.
     */
    default SignUpResponse toSignUpResponse(Member member) {
        return new SignUpResponse(member.getUsername(), AuthConstants.SIGNUP_SUCCESS_MESSAGE);
    }

    /**
     * Member + AuthTokens → LoginResponse
     *
     * LoginResponse는 tokens과 member 두 소스가 필요하므로 MapStruct 자동 생성이 불가합니다.
     * toUserInfoDto()를 재사용해 중복 없이 조립합니다.
     */
    default LoginResponse toLoginResponse(Member member, AuthTokens tokens) {
        return new LoginResponse(tokens, toUserInfoDto(member));
    }
}
