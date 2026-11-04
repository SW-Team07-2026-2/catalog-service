package com.indie.catalog.domain.search.service;

import com.indie.catalog.global.common.exception.GeneralException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchServiceTest {

    @Test
    void blankOrTooLongQueryIs400() {
        SearchService service = new SearchService(null, null);
        assertThatThrownBy(() -> service.search("  ", 20)).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> service.search("가".repeat(51), 20)).isInstanceOf(GeneralException.class);
    }
}
