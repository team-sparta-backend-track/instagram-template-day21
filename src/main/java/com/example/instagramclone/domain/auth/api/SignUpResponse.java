package com.example.instagramclone.domain.auth.api;

import lombok.Builder;

/**
 * 회원가입 성공 시 클라이언트에게 반환할 데이터입니다.
 *
 * [Record 사용에 대한 고찰]
 * Java 14부터 도입된 Record는 불변(immutable) 데이터 객체를 간결하게 생성할 수 있게 해줍니다.
 *
 * 장점:
 * 1. 간결성: getter, constructor, equals, hashCode, toString 등을 자동으로 생성해주어 보일러플레이트 코드가 획기적으로 줄어듭니다.
 * 2. 불변성: 모든 필드가 final로 선언되므로 데이터가 변경되지 않음을 보장합니다 (Thread-safe).
 * 3. 의도 명확성: "데이터를 단순히 나르기 위한 캐리어(DTO)"라는 의도가 명확해집니다.
 *
 * 단점 (Trade-off):
 * 1. 상속 불가: Record는 다른 클래스를 상속받을 수 없습니다 (인터페이스 구현은 가능).
 * 2. 라이브러리 호환성: 일부 구버전 라이브러리(Jackson, Hibernate 등)에서 지원하지 않을 수 있습니다 (현재 버전에서는 문제 없음).
 * 3. 유연성 부족: 내부에 복잡한 로직을 넣거나 필드 값을 변경해야 하는 경우에는 적합하지 않습니다.
 */
@Builder
public record SignUpResponse(
        String username,
        String message
) {

}
