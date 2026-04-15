package com.example.instagramclone.domain.auth.api;


public record LoginResponse(
        AuthTokens tokens,
        UserInfoDto user
) {


    public record UserInfoDto(
            Long id,
            String username,
            String name,
            String profileImageUrl
    ) {

    }
}
