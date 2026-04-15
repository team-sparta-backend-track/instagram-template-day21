package com.example.instagramclone.domain.post.domain;

import com.example.instagramclone.domain.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * [Day 12] PostLike 조회·삭제.
 *
 * - findByMemberAndPostIn: 피드 Step 4 — 동일 Member + Post 목록에 대한 좋아요 배치 조회 (existsByMemberAndPost 와 타입 줄 맞춤)
 */
import com.example.instagramclone.domain.post.infrastructure.PostLikeRepositoryCustom;

public interface PostLikeRepository extends JpaRepository<PostLike, Long>, PostLikeRepositoryCustom {

    boolean existsByMemberAndPost(Member member, Post post);

    void deleteByMemberAndPost(Member member, Post post);

    long countByPost(Post post);

    List<PostLike> findByMemberAndPostIn(Member member, List<Post> posts);
}
