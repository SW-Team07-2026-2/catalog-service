package com.indie.catalog.global.common.util;

/**
 * 부분 일치 검색 패턴 — lower(컬럼) like :pattern escape '\' 에 넣는 값을 만듭니다.
 * 컬럼 쪽을 lower() 로 감싸는 이유: V5 의 트라이그램 인덱스가 lower(이름) 식 인덱스입니다.
 */
public final class LikePattern {

    public static final char ESCAPE = '\\';

    private LikePattern() {}

    /** 소문자로 바꾸고 앞뒤에 % 를 붙입니다. 검색어의 % · _ 는 글자 그대로 — 안 그러면 "%" 한 글자로 전체가 나옵니다 */
    public static String contains(String q) {
        return "%" + q.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
