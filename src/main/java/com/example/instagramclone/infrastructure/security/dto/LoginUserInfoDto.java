package com.example.instagramclone.infrastructure.security.dto;

import lombok.Builder;

/**
 * 인증된 유저의 최소 정보를 담는 DTO
 * 컨트롤러에서 @LoginUser 로 주입받아 사용하며, 추후 권한/닉네임 등으로 확장 가능합니다.
 */
@Builder
public record LoginUserInfoDto(
        Long id
) {
}
