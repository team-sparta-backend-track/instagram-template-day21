package com.example.instagramclone.domain.auth.api;


import com.example.instagramclone.core.aop.annotation.Masking;

public record LoginRequest(
    String username,

    @Masking
    String password
) {
}
