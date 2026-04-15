package com.example.instagramclone.domain.post.infrastructure;

import com.example.instagramclone.domain.post.api.LikeStatusResponse;
import com.example.instagramclone.domain.post.api.PostImageResponse;
import com.example.instagramclone.domain.post.api.PostResponse;
import com.example.instagramclone.domain.post.api.ProfilePostResponse;
import com.example.instagramclone.domain.post.domain.Post;
import com.example.instagramclone.domain.post.domain.PostImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Post 도메인 MapStruct 매퍼.
 *
 * [componentModel = "spring"]
 * 컴파일 시 PostMapperImpl을 생성하고 스프링 Bean으로 등록합니다.
 * → PostService에서 생성자 주입으로 바로 사용 가능
 *
 * [./gradlew compileJava 후 확인할 것]
 * build/generated/sources/annotationProcessor/.../PostMapperImpl.java
 *
 * <p>TODO Day 16 Step 2: {@code PostResponse}·상세 DTO에 {@code List<String> hashtagNames} 를 붙이면
 * 해시태그 배치 조회 결과를 인자로 받는 {@code default} 조립 메서드를 여기에 추가합니다.
 */
@Mapper(componentModel = "spring")
public interface PostMapper {

    /**
     * PostImage → PostImageResponse
     * imgOrder(엔티티) → imageOrder(DTO) 이름이 달라 @Mapping 명시 필요.
     * 나머지 id, imageUrl은 이름이 같으므로 자동 매핑됩니다.
     */
    @Mapping(source = "imgOrder", target = "imageOrder")
    PostImageResponse toImageResponse(PostImage postImage);

    /**
     * List<PostImage> → List<PostImageResponse>
     * 위 toImageResponse()를 참고하여 MapStruct가 구현체를 자동 생성합니다.
     */
    List<PostImageResponse> toImageResponses(List<PostImage> postImages);


    /**
     * Post + List<PostImage> + liked + commentCount → PostResponse
     *
     * likeCount는 Post 비정규화 컬럼(엔티티 필드) 값을 사용합니다.
     */
    default PostResponse toResponse(Post post, List<PostImage> images, boolean liked, long commentCount, List<String> hashtagNames) {

        if (post == null) {
            return null;
        }

        return new PostResponse(
                post.getId(),
                post.getContent(),
                post.getWriter().getUsername(),
                post.getWriter().getProfileImageUrl(),
                toImageResponses(images),
                post.getCreatedAt(),
                new LikeStatusResponse(liked, post.getLikeCount()),
                commentCount,
                hashtagNames == null ? List.of() : hashtagNames
        );
    }

    /**
     * Post + 해당 게시글의 이미지 리스트 → ProfilePostResponse
     *
     * 프로필 그리드에서는 썸네일(첫 번째 이미지)만 필요하므로,
     * 전체 이미지 리스트를 받아서 서비스 레이어에서 imgOrder 기준으로 정렬 후 전달합니다.
     * likeCount는 Post 비정규화 값. commentCount는 댓글 미구현 시 0.
     */
    default ProfilePostResponse toProfilePostResponse(Post post, List<PostImage> images) {

        if (post == null) {
            return null;
        }

        String thumbnailUrl = images.isEmpty() ? null : images.get(0).getImageUrl();
        boolean multipleImages = images.size() > 1;

        return new ProfilePostResponse(
                post.getId(),
                thumbnailUrl,
                multipleImages,
                post.getLikeCount(),
                0
        );
    }
}
