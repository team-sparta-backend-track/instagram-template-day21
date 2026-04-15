package com.example.instagramclone.domain.hashtag.infrastructure;

import com.example.instagramclone.domain.hashtag.api.HashtagMetaResponse;
import com.example.instagramclone.domain.hashtag.domain.Hashtag;
import org.mapstruct.Mapper;

/**
 * 해시태그 MapStruct 매퍼.
 *
 * <p>Day 16 라이브 코딩에서 {@code @Mapping}·다중 소스 파라미터 등으로 확장할 수 있도록
 * 우선 default 메서드로 두었습니다. (컴파일러 {@code -parameters} 와 함께면 Bean 구현체가 생성됩니다.)
 */
@Mapper(componentModel = "spring")
public interface HashtagMapper {

    default HashtagMetaResponse toMetaResponse(Hashtag hashtag, long postCount) {
        if (hashtag == null) {
            return null;
        }
        return new HashtagMetaResponse(hashtag.getName(), postCount);
    }
}
