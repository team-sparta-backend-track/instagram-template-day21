package com.example.instagramclone.domain.hashtag.domain;

import com.example.instagramclone.domain.hashtag.infrastructure.HashtagRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HashtagRepository extends JpaRepository<Hashtag, Long>, HashtagRepositoryCustom {

    Optional<Hashtag> findByName(String name);

    boolean existsByName(String name);
}
