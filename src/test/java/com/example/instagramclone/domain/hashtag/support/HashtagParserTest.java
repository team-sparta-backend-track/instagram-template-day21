package com.example.instagramclone.domain.hashtag.support;

import com.example.instagramclone.core.exception.HashtagErrorCode;
import com.example.instagramclone.core.exception.HashtagException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashtagParserTest {

    @Test
    @DisplayName("캡션에서 허용 문자만 추출하고 소문자·첫 등장 순서로 중복 제거한다")
    void extracts_normalized_unique_in_order() {
        var tags = HashtagParser.extractNormalizedUniqueTags("안녕 #Cafe #카페 #cafe 다시 #Cafe");
        assertThat(tags).containsExactly("cafe", "카페");
    }

    @Test
    @DisplayName("빈 문자열·null은 빈 목록")
    void blank_returns_empty() {
        assertThat(HashtagParser.extractNormalizedUniqueTags(null)).isEmpty();
        assertThat(HashtagParser.extractNormalizedUniqueTags("   ")).isEmpty();
    }

    @Test
    @DisplayName("태그 이름이 상한을 넘으면 HASHTAG_NAME_TOO_LONG")
    void too_long_throws() {
        String longToken = "a".repeat(HashtagParser.MAX_TAG_LENGTH + 1);
        assertThatThrownBy(() -> HashtagParser.extractNormalizedUniqueTags("#" + longToken))
                .isInstanceOf(HashtagException.class)
                .hasFieldOrPropertyWithValue("errorCode", HashtagErrorCode.HASHTAG_NAME_TOO_LONG);
    }

    @Test
    @DisplayName("서로 다른 태그가 상한을 넘으면 TOO_MANY_HASHTAGS")
    void too_many_unique_throws() {
        String caption = IntStream.range(0, HashtagParser.MAX_TAGS_PER_POST + 1)
                .mapToObj(i -> "#t" + i)
                .collect(Collectors.joining(" "));
        assertThatThrownBy(() -> HashtagParser.extractNormalizedUniqueTags(caption))
                .isInstanceOf(HashtagException.class)
                .hasFieldOrPropertyWithValue("errorCode", HashtagErrorCode.TOO_MANY_HASHTAGS);
    }
}
