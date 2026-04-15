package com.example.instagramclone.domain.hashtag.application;

import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.core.exception.HashtagErrorCode;
import com.example.instagramclone.core.exception.HashtagException;
import com.example.instagramclone.core.exception.PostErrorCode;
import com.example.instagramclone.core.exception.PostException;
import com.example.instagramclone.domain.hashtag.api.HashtagMetaResponse;
import com.example.instagramclone.domain.hashtag.domain.Hashtag;
import com.example.instagramclone.domain.hashtag.domain.HashtagRepository;
import com.example.instagramclone.domain.hashtag.domain.PostHashtag;
import com.example.instagramclone.domain.hashtag.domain.PostHashtagRepository;
import com.example.instagramclone.domain.hashtag.support.HashtagParser;
import com.example.instagramclone.domain.post.api.ProfilePostResponse;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 해시태그 파싱·영속화·메타·태그 피드 (Day 16 라이브 코딩 대상).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HashtagService {

    /**
     * 해시태그 마스터(이름 유니크)
     */
    private final HashtagRepository hashtagRepository;
    /**
     * 게시물–해시태그 연결 행만 관리 (Post/Hashtag에 컬렉션 두지 않음)
     */
    private final PostHashtagRepository postHashtagRepository;
    private final PostRepository postRepository;

    /**
     * 캡션을 파싱한 뒤 기존 {@link PostHashtag} 를 비우고, 현재 본문 기준으로 다시 맞춥니다 (작성·수정 공통).
     */
    @Transactional
    public void syncHashtagsForPost(Long postId, String caption) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        List<String> normalizedTags = HashtagParser.extractNormalizedUniqueTags(caption);

        // 1. 기존 연결된 hashtag_id만 SELECT (엔티티 전체 로드 X, N+1 방지)
        List<Long> existingHashtagIds = postHashtagRepository.findByPost_IdIn(List.of(postId)).stream()
                .map(ph -> ph.getHashtag().getId())
                .toList();

        // 2. 기존 태그들의 postCount -1
        for (Long hashtagId : existingHashtagIds) {
            Hashtag hashtag = hashtagRepository.findById(hashtagId)
                    .orElseThrow(() -> new HashtagException(HashtagErrorCode.HASHTAG_NOT_FOUND));
            hashtag.decrementPostCount();
        }

        // 3. 기존 연결 전부 bulk delete (영속 컨텍스트와의 불일치 방지)
        postHashtagRepository.deleteByPost_Id(postId);

        // 4. 새 태그 추가하면서 +1
        for (String name : normalizedTags) {
            Hashtag hashtag = hashtagRepository.findByName(name)
                    .orElseGet(() -> hashtagRepository.save(Hashtag.create(name)));

            hashtag.incrementPostCount();          // 🔥 비정규화 핵심!
            postHashtagRepository.save(PostHashtag.create(post, hashtag));
        }
    }

    /**
     * 여러 게시물에 붙은 해시태그 이름을 배치 조회합니다. 피드·상세 DTO 조립용 (엔티티 컬렉션에 의존하지 않음).
     */
    public Map<Long, List<String>> findHashtagNamesByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }

        // IN 한 번으로 연결 행만 가져온 뒤 메모리에서 postId별로 묶는다 (N+1 회피)
        List<PostHashtag> links = postHashtagRepository.findByPost_IdIn(postIds);
        // 같은 게시물에 달린 PostHashtag 여러 개를 postId 기준으로 그룹핑
        Map<Long, List<PostHashtag>> byPost = links.stream()
                .collect(Collectors.groupingBy(ph -> ph.getPost().getId()));

        // 호출자가 넘긴 postIds 순서를 유지 (피드 행 순서와 맞추기 위함)
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (Long postId : postIds) {
            List<PostHashtag> row = byPost.getOrDefault(postId, List.of());
            // 연결 행 삽입 순서(= id 오름차순)대로 태그명 나열 — sync 시 저장 순서와 대체로 일치
            List<String> names = row.stream()
                    .sorted(Comparator.comparing(PostHashtag::getId))
                    .map(ph -> ph.getHashtag().getName())
                    .toList();
            result.put(postId, names);
        }
        return result;
    }

    /**
     * 정규화된 태그명 기준 게시물 목록 — {@link ProfilePostResponse} + {@link SliceResponse} 는 프로필 그리드·무한 스크롤과 동일 계약.
     */
    public SliceResponse<ProfilePostResponse> getPostsByHashtag(String name, Pageable pageable) {
        String normalized = HashtagParser.normalizeHashtagName(name);
        if (normalized.isBlank()) {
            throw new HashtagException(HashtagErrorCode.INVALID_HASHTAG_TOKEN);
        }
        if (!hashtagRepository.existsByName(normalized)) {
            throw new HashtagException(HashtagErrorCode.HASHTAG_NOT_FOUND);
        }

        Slice<ProfilePostResponse> slice = hashtagRepository.findProfilePostSliceByHashtagName(normalized, pageable);
        return SliceResponse.of(slice.hasNext(), slice.getContent());
    }

    /** 커서 기반 해시태그 게시물 조회 */
    public SliceResponse<ProfilePostResponse> getPostsByHashtagByCursor(String name, Long cursorId, int size) {
        String normalized = HashtagParser.normalizeHashtagName(name);
        if (normalized.isBlank()) {
            throw new HashtagException(HashtagErrorCode.INVALID_HASHTAG_TOKEN);
        }
        if (!hashtagRepository.existsByName(normalized)) {
            throw new HashtagException(HashtagErrorCode.HASHTAG_NOT_FOUND);
        }

        Slice<ProfilePostResponse> slice = hashtagRepository.findProfilePostSliceByHashtagNameByCursor(normalized, cursorId, size);
        return SliceResponse.of(slice.hasNext(), slice.getContent());
    }

    /**
     * 해시태그 추천 목록 (Top N).
     *
     * <p>prefix 가 비어있으면 전체 Top N을 반환합니다.
     */
    public List<HashtagMetaResponse> getSuggestions(String prefix, int limit) {
        int safeLimit = Math.max(0, limit);
        if (safeLimit == 0) {
            return List.of();
        }

        String normalizedPrefix = HashtagParser.normalizeHashtagName(prefix);
        List<HashtagMetaResponse> metas = hashtagRepository.findTopSuggestions(normalizedPrefix, safeLimit);
        if (metas == null || metas.isEmpty()) {
            return List.of();
        }
        return metas;
    }
}
