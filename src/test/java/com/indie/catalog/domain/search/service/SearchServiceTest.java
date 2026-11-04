package com.indie.catalog.domain.search.service;

import com.indie.catalog.global.common.exception.GeneralException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchServiceTest {

    /** 빈 검색어 · 50자 초과는 코드가 따로 (둘 다 400) */
    @Test
    void blankOrTooLongQueryIs400() {
        SearchService service = new SearchService(null, null);
        assertThatThrownBy(() -> service.search("  ", 20))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode().getCode()).isEqualTo("SEARCH_QUERY_EMPTY"));
        assertThatThrownBy(() -> service.search("가".repeat(51), 20))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode().getCode()).isEqualTo("SEARCH_QUERY_TOO_LONG"));
    }
}
