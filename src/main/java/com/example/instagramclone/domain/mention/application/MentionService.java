package com.example.instagramclone.domain.mention.application;

import com.example.instagramclone.domain.comment.domain.Comment;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.mention.domain.CommentMention;
import com.example.instagramclone.domain.mention.domain.CommentMentionRepository;
import com.example.instagramclone.domain.mention.support.MentionParser;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 댓글 본문에서 @username을 추출하고, 실제 존재하는 유저만 CommentMention으로 저장합니다.
 * HashtagService.syncHashtagsForPost()와 동일한 흐름입니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentionService {

    private final CommentMentionRepository commentMentionRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 댓글 본문에서 @username을 추출 → 실제 존재하는 유저만 CommentMention 저장.
     *
     * ① 기존 멘션 삭제 (댓글 수정 대응)
     * ② 본문 파싱 → username 목록 추출
     * ③ DB에서 실제 존재하는 유저만 IN 쿼리 1회로 조회
     * ④ CommentMention 생성 후 저장
     */
    @Transactional
    public void syncMentionsForComment(Comment comment, String content) {
        // ① 기존 멘션 삭제
        commentMentionRepository.deleteAllByCommentId(comment.getId());

        // ② 파서로 @username 추출
        List<String> usernames = MentionParser.extractMentionedUsernames(content);
        if (usernames.isEmpty()) return;

        // ③ DB에서 실제 존재하는 유저만 한 번에 조회 (IN 쿼리 1회)
        List<Member> existingMembers = memberRepository.findAllByUsernameIn(usernames);

        // ④ CommentMention 생성 후 저장
        List<CommentMention> mentions = existingMembers.stream()
                .map(member -> CommentMention.create(comment, member))
                .toList();

        commentMentionRepository.saveAll(mentions);

        // ✅ 이벤트 발행 — 멘션된 각 유저에게 알림
        for (Member mentioned : existingMembers) {
            eventPublisher.publishEvent(new NotificationEvent(
                    NotificationType.MENTION,
                    mentioned.getId(),               // receiver = 멘션된 사람
                    comment.getWriter().getId(),     // sender = 댓글 작성자
                    comment.getPost().getId(),       // target = 게시물
                    null
            ));
        }
    }
}
