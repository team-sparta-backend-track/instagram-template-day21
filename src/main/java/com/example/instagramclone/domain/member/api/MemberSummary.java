package com.example.instagramclone.domain.member.api;

import lombok.Builder;

@Builder
public record MemberSummary(
        Long memberId,
        String username,
        String profileImageUrl
) {
}
