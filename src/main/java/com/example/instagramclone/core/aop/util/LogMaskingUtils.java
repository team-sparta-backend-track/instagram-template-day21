package com.example.instagramclone.core.aop.util;


import com.example.instagramclone.core.aop.annotation.Masking;
import com.example.instagramclone.core.aop.annotation.MaskingType;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class LogMaskingUtils {

    /**
     * 파라미터 배열을 순회하면서 객체 내부의 @Masking 필드를 찾아 가려주는 문자열을 반환합니다.
     */
    public static String buildMaskedParamsString(String[] parameterNames, Object[] args) {
        if (args == null || args.length == 0) {
            return "없음";
        }

        List<String> paramList = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String paramName = parameterNames != null ? parameterNames[i] : "arg" + i;
            Object arg = args[i];

            if (arg == null) {
                paramList.add(paramName + "=null");
                continue;
            }

            // DTO 객체인 경우 (자바 기본형, String 등이 아닌 경우) 리플렉션으로 내부 필드 검사
            if (!isWrapperType(arg.getClass()) && arg.getClass() != String.class) {
                String maskedObjectString = applyMaskingToObject(arg);
                paramList.add(paramName + "=" + maskedObjectString);
            } else {
                paramList.add(paramName + "=" + arg);
            }
        }
        return StringUtils.collectionToCommaDelimitedString(paramList);
    }

    /**
     * DTO 객체의 필드들을 리플렉션으로 읽어 @Masking 애노테이션이 있으면 "******"로 덮어씁니다.
     */
    private static String applyMaskingToObject(Object obj) {
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        List<String> fieldStrings = new ArrayList<>();

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);

                // [과제 2 예시답안] 필드에 붙은 @Masking 어노테이션 객체를 직접 가져옵니다.
                Masking masking = field.getAnnotation(Masking.class);

                // 필드에 @Masking 애노테이션이 붙어있다면 값 숨김 처리
                if (masking != null && value != null) {
                    // 어노테이션에 정의된 type을 보고 동적으로 마스킹 처리!
                    String maskedValue = maskValueByType(value.toString(), masking.type());
                    fieldStrings.add(field.getName() + "='" + maskedValue + "'");
                } else {
                    fieldStrings.add(field.getName() + "=" + value);
                }
            } catch (IllegalAccessException e) {
                fieldStrings.add(field.getName() + "=ERROR");
            }
        }
        return clazz.getSimpleName() + "{" + StringUtils.collectionToCommaDelimitedString(fieldStrings) + "}";
    }

    // [과제 2 예시답안] 타입별 동적 마스킹 비즈니스 로직
    private static String maskValueByType(String value, MaskingType type) {
        return switch (type) {
            case PASSWORD -> "******";
            case EMAIL -> {
                int atIndex = value.indexOf('@');
                if (atIndex == -1) {
                    // '@'가 없는 경우 (예: 전화번호) -> 뒤 4자리 마스킹 처리
                    if (value.length() <= 4) yield "****";
                    yield value.substring(0, value.length() - 4) + "****";
                }
                // 이메일 형식이 아니거나 앞자리가 너무 짧으면 통째로 마스킹
                if (atIndex <= 3) yield "***" + value.substring(atIndex);
                // 앞 3글자 + *** + @도메인
                yield value.substring(0, 3) + "***" + value.substring(atIndex);
            }
            case NAME -> {
                if (value.length() <= 1) yield "*";
                if (value.length() == 2) yield value.charAt(0) + "*";
                // 홍길동 -> 홍*동, 남궁민수 -> 남**수
                yield value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
            }
        };
    }

    /**
     * 원시(Primitive) 타입 래퍼 클래스인지 확인하는 유틸리티 메서드
     */
    private static boolean isWrapperType(Class<?> clazz) {
        return clazz == Boolean.class || clazz == Character.class ||
               clazz == Byte.class || clazz == Short.class ||
               clazz == Integer.class || clazz == Long.class ||
               clazz == Float.class || clazz == Double.class;
    }
}
