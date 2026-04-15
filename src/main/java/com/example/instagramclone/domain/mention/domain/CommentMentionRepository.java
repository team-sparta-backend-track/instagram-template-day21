package com.example.instagramclone.domain.mention.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {

    /** 댓글 수정 시 기존 멘션을 지우고 새로 저장하기 위한 삭제 메서드 */
    void deleteAllByCommentId(Long commentId);
}
