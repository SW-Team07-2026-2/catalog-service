package com.indie.catalog.domain.follow.client;

import com.indie.catalog.global.auth.InternalTokenFilter;
import com.indie.catalog.global.auth.InternalTokenIssuer;
import com.indie.catalog.global.common.filter.CorrelationIdFilter;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.config.CatalogProperties;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog -> Concert POST /internal/concerts/artists/latest 호출.
 * 내부 토큰으로 인증하고 아티스트 ID 목록을 본문에 전달합니다.
 */
@Component
public class HttpConcertClient implements ConcertClient {

    private final RestClient rest;
    private final InternalTokenIssuer tokens;

    public HttpConcertClient(CatalogProperties props, InternalTokenIssuer tokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.concert().connectTimeoutMs());
        factory.setReadTimeout(props.concert().readTimeoutMs());
        this.rest = RestClient.builder().baseUrl(props.concert().baseUrl()).requestFactory(factory).build();
        this.tokens = tokens;
    }

    @Override
    public Map<Long, OffsetDateTime> latestConcertAt(List<Long> artistIds) {
        try {
            ApiResponse<Items> res = rest.post().uri("/internal/concerts/artists/latest")
                    .headers(h -> {
                        h.set(InternalTokenFilter.HEADER, tokens.issue("concert"));
                        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
                        if (id != null) h.set(CorrelationIdFilter.HEADER, id);
                    })
                    .body(new Request(artistIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Items>>() {});
            if (res == null || res.result() == null || res.result().items() == null) throw new RestClientException("empty latest response");
            Map<Long, OffsetDateTime> latest = new HashMap<>();
            res.result().items().forEach(i -> latest.put(i.artistId(), i.latestAt()));
            return latest;
        } catch (RestClientException e) {
            throw new Unavailable(e);
        }
    }

    record Request(List<Long> artistIds) {}

    record Items(List<Latest> items) {}

    record Latest(long artistId, OffsetDateTime latestAt) {}
}
