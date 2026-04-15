package com.example.instagramclone.domain.mention.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 본문에서 @username 멘션을 추출합니다.
 * HashtagParser와 동일한 구조 — '#' 대신 '@'를 사용합니다.
 */
public final class MentionParser {

    public static final int MAX_MENTION_LENGTH = 30;
    public static final int MAX_MENTIONS_PER_CONTENT = 20;

    // HashtagParser: #([0-9A-Za-z가-힣_]+)
    // MentionParser: @([0-9A-Za-z가-힣_]+)  ← '#' → '@'
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([0-9A-Za-z가-힣_]+)");

    private MentionParser() {}

    /**
     * 본문에서 @username을 추출하고, 정규화(소문자)·중복 제거·길이 제한을 수행합니다.
     * 존재하지 않는 유저 필터링은 Service에서 처리합니다.
     */
    public static List<String> extractMentionedUsernames(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        Matcher matcher = MENTION_PATTERN.matcher(content);
        Set<String> orderedUnique = new LinkedHashSet<>();

        while (matcher.find()) {
            String raw = matcher.group(1);
            String normalized = raw.strip().toLowerCase();

            if (normalized.isEmpty()) continue;
            if (normalized.length() > MAX_MENTION_LENGTH) continue;

            orderedUnique.add(normalized);
        }

        List<String> result = new ArrayList<>(orderedUnique);
        if (result.size() > MAX_MENTIONS_PER_CONTENT) {
            result = result.subList(0, MAX_MENTIONS_PER_CONTENT);
        }

        return result;
    }
}
