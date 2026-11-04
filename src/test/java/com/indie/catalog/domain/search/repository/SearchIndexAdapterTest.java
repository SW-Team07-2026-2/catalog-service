package com.indie.catalog.domain.search.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchIndexAdapterTest {

    /** 검색어의 % · _ 가 와일드카드로 동작하면 "%" 한 글자로 전체가 나옵니다 */
    @Test
    void likeWildcardsAreEscaped() {
        assertThat(SearchIndexAdapter.escapeLike("100%_\\")).isEqualTo("100\\%\\_\\\\");
    }
}
