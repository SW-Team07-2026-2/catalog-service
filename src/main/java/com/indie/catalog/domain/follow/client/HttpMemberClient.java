package com.indie.catalog.domain.follow.client;

import com.indie.catalog.global.auth.InternalTokenFilter;
import com.indie.catalog.global.auth.InternalTokenIssuer;
import com.indie.catalog.global.common.filter.CorrelationIdFilter;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.config.CatalogProperties;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Catalog -> Member 팔로우 전용 포트(8181)의 내부 API 호출.
 * 회원 팔로우 목록과 아티스트별 팔로워 수를 내부 토큰으로 조회합니다.
 */
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
                    .headers(this::internalHeaders)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Items>>() {});
            if (res == null || res.result() == null || res.result().items() == null) throw new RestClientException("empty follows response");
            return res.result().items();
        } catch (RestClientException e) {
            throw new Unavailable(e);
        }
    }

    @Override
    public Map<Long, Long> followerCounts(Collection<Long> artistIds) {
        String ids = artistIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            ApiResponse<Counts> res = rest.get().uri(u -> u.path("/internal/artists/follower-counts").queryParam("artistIds", ids).build())
                    .headers(this::internalHeaders)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Counts>>() {});
            if (res == null || res.result() == null || res.result().items() == null) throw new RestClientException("empty follower counts response");
            return res.result().items().stream().collect(Collectors.toMap(Count::artistId, Count::followerCount));
        } catch (RestClientException e) {
            throw new Unavailable(e);
        }
    }

    private void internalHeaders(HttpHeaders h) {
        h.set(InternalTokenFilter.HEADER, tokens.issue("member"));
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (id != null) h.set(CorrelationIdFilter.HEADER, id);
    }

    record Items(List<FollowedArtist> items) {}

    record Counts(List<Count> items) {}

    record Count(long artistId, long followerCount) {}
}
