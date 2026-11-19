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

import java.util.List;

/** Catalog → Member 내부 호출 (X-Internal-Token · iss=catalog · aud=member) — GET /internal/members/{userId}/follows */
@Component
public class HttpMemberClient implements MemberClient {

    private final RestClient rest;
    private final InternalTokenIssuer tokens;

    public HttpMemberClient(CatalogProperties props, InternalTokenIssuer tokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.member().connectTimeoutMs());
        factory.setReadTimeout(props.member().readTimeoutMs());
        this.rest = RestClient.builder().baseUrl(props.member().baseUrl()).requestFactory(factory).build();
        this.tokens = tokens;
    }

    @Override
    public List<FollowedArtist> follows(long userId) {
        try {
            ApiResponse<Items> res = rest.get().uri("/internal/members/{userId}/follows", userId)
                    .headers(h -> {
                        h.set(InternalTokenFilter.HEADER, tokens.issue("member"));
                        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
                        if (id != null) h.set(CorrelationIdFilter.HEADER, id);
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Items>>() {});
            if (res == null || res.result() == null || res.result().items() == null) throw new RestClientException("empty follows response");
            return res.result().items();
        } catch (RestClientException e) {
            throw new Unavailable(e);
        }
    }

    record Items(List<FollowedArtist> items) {}
}
