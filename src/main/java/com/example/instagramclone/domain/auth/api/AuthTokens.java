package com.example.instagramclone.domain.auth.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record AuthTokens(
        String accessToken,
        @JsonIgnore
        String refreshToken
) {}
