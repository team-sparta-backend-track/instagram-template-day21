package com.example.instagramclone.domain.post.infrastructure;

import java.util.List;
import java.util.Map;

public interface PostLikeRepositoryCustom {

    /**
     * Day 15 Live Coding: 피드 카드 배치 메트릭 조회용
     * 주어진 게시글 ID 목록에 대해 각 게시글의 좋아요 개수를 Map 형태로 반환합니다.
     */
    Map<Long, Long> countLikesByPostIds(List<Long> postIds);
}
