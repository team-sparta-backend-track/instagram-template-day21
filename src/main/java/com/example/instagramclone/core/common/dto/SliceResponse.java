package com.example.instagramclone.core.common.dto;

import java.util.List;

/**
 * 무한 스크롤 / 목록 조회에 공통으로 사용할 수 있는 범용 Slice 응답 DTO.
 *
 * 기존 Feed 전용 응답에서 이름을 일반화해,
 * 피드 / 프로필 게시물 / 팔로워 목록 / 팔로잉 목록 등에도 재사용할 수 있다.
 */
public record SliceResponse<T>(
        boolean hasNext,
        List<T> items
) {
    public static <T> SliceResponse<T> of(boolean hasNext, List<T> items) {
        return new SliceResponse<>(hasNext, items);
    }
}
