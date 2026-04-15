package com.example.instagramclone.domain.hashtag.support;

import com.example.instagramclone.core.exception.HashtagErrorCode;
import com.example.instagramclone.core.exception.HashtagException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 캡션 문자열에서 해시태그 토큰을 추출·검증합니다.
 *
 * <p>팀 규칙: {@code #} 뒤에 영문·숫자·한글·밑줄만 허용 ({@link #HASHTAG_PATTERN}).
 */
public final class HashtagParser {

    /** 저장 컬럼 길이({@link com.example.instagramclone.domain.hashtag.domain.Hashtag} {@code name})와 맞춤 */
    public static final int MAX_TAG_LENGTH = 30;

    /** 스팸 방지: 게시물당 최대 태그 개수 */
    public static final int MAX_TAGS_PER_POST = 30;

    /** {@code #태그} 전체는 0번 캡처 그룹, 괄호 안 태그명만 1번 그룹으로 캡처한다. */
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([0-9A-Za-z가-힣_]+)");

    private HashtagParser() {}

    /**
     * 본문에서 해시태그를 파싱해 저장용 정규화 이름 목록으로 반환합니다 (첫 등장 순서 유지, 중복 제거).
     *
     * @throws HashtagException 규칙 위반 시
     */
    public static List<String> extractNormalizedUniqueTags(String caption) {
        if (caption == null || caption.isBlank()) {
            return List.of();
        }

        // 캡션 전체에 대해 정규식을 적용할 Matcher (한 번 만들고, 끝까지 find()로 순회)
        Matcher matcher = HASHTAG_PATTERN.matcher(caption);
        // 삽입 순서를 유지하면서 중복 문자열은 하나로 묶는다 (같은 태그가 여러 번 나와도 한 번만 유지)
        Set<String> orderedUnique = new LinkedHashSet<>();

        // find(): 입력 문자열에서 정규식과 맞는 '다음' 구간을 찾는다. 첫 호출은 맨 앞부터,
        // 이후 호출마다 이전에 찾은 위치 뒤부터 이어서 검색한다 (전역 검색 = while과 함께 쓰는 패턴).
        // 매칭이 없으면 false → 루프 종료. (matches()는 전체 문자열이 한 번에 맞아야 하므로 여기서는 부적합)
        while (matcher.find()) {
            // group(1): 패턴에서 첫 번째 괄호(...)로 묶인 부분만 반환 → '#'은 제외된 태그 본문
            //   (group(0)이면 '#맛집' 전체, group(1)이면 '맛집'만)
            String raw = matcher.group(1);
            String normalized = normalizeHashtagName(raw);

            if (normalized.isEmpty()) continue;

            if (normalized.length() > MAX_TAG_LENGTH) {
                throw new HashtagException(HashtagErrorCode.HASHTAG_NAME_TOO_LONG);
            }

            orderedUnique.add(normalized);
        }

        if (orderedUnique.size() > MAX_TAGS_PER_POST) {
            throw new HashtagException(HashtagErrorCode.TOO_MANY_HASHTAGS);
        }

        // LinkedHashSet은 순회 순서와 동일하게 ArrayList로 복사 가능 (불변 List로 반환)
        return new ArrayList<>(orderedUnique);
    }

    public static String normalizeHashtagName(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return "";
        }
        String t = rawToken.strip();
        // 파싱 단계에서 group(1)만 쓰면 '#'이 없지만, 외부에서 넘어올 수 있어 방어적으로 제거
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        // DB·검색 키는 소문자로 통일 (#Cafe 와 #cafe 동일 태그)
        return t.toLowerCase();
    }
}