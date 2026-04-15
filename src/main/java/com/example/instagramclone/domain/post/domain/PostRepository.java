package com.example.instagramclone.domain.post.domain;

import com.example.instagramclone.domain.post.infrastructure.PostRepositoryCustom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

    /**
     * [Day 18 Step 2] 비관적 락(Pessimistic Lock)으로 게시물 조회.
     *
     * <p>DB에 {@code SELECT ... FOR UPDATE} 쿼리를 발행한다.
     * 이 메서드를 호출한 트랜잭션이 끝날 때까지 <b>같은 행을 다른 트랜잭션이 수정하지 못한다</b>.
     * 동시에 100명이 좋아요를 눌러도 한 번에 한 트랜잭션씩 순서대로 처리되므로
     * likeCount 유실(Race Condition)이 발생하지 않는다.
     *
     * <p>단점: 대기 요청이 많아질수록 DB 커넥션을 오래 점유하므로
     * 트래픽이 폭증하면 커넥션 풀이 고갈될 위험이 있다.
     *
     * @param id 조회할 게시물 ID
     * @return 락이 걸린 Post Optional
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :id")
    Optional<Post> findByIdWithLock(@Param("id") Long id);
}
