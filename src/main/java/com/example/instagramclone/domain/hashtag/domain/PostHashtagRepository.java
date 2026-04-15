package com.example.instagramclone.domain.hashtag.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, Long> {

    /**
     * {@link PostHashtag#post} 연관의 id 기준 삭제 (Spring Data: {@code post} + {@code id} → {@code Post_Id}).
     */
    void deleteByPost_Id(Long postId);

    List<PostHashtag> findByPost_Id(Long postId);

    @EntityGraph(attributePaths = {"post", "hashtag"})
    List<PostHashtag> findByPost_IdIn(Collection<Long> postIds);

    long countByHashtag_Id(Long hashtagId);
}
