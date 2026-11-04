package com.indie.catalog.domain.order.client;

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

import java.util.List;

/** Catalog → Payment 내부 호출 (X-Internal-Token · iss=catalog · aud=payment). 타임아웃 3초 */
@Component
public class HttpPaymentClient implements PaymentClient {

    static final String ORDER_TYPE = "GOODS";

    private final RestClient rest;
    private final InternalTokenIssuer tokens;

    public HttpPaymentClient(CatalogProperties props, InternalTokenIssuer tokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.payment().connectTimeoutMs());
        factory.setReadTimeout(props.payment().readTimeoutMs());
        this.rest = RestClient.builder().baseUrl(props.payment().baseUrl()).requestFactory(factory).build();
        this.tokens = tokens;
    }

    @Override
    public Prepared prepare(long orderId, long userId, int amount, List<Long> albumIds) {
        try {
            ApiResponse<Prepared> res = rest.post().uri("/internal/payments/prepare")
                    .headers(h -> {
                        h.set(InternalTokenFilter.HEADER, tokens.issue("payment"));
                        correlationId(h);
                    })
                    .body(new PrepareRequest(ORDER_TYPE, String.valueOf(orderId), userId, amount, albumIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Prepared>>() {});
            if (res == null || res.result() == null) throw new RestClientException("empty prepare response");
            return res.result();
        } catch (RestClientException e) {
            throw new Unavailable("prepare", e);
        }
    }

    @Override
    public String cancel(long orderId, String reason, boolean refundApproved) {
        try {
            return rest.post().uri("/internal/payments/cancel")
                    .headers(h -> {
                        h.set(InternalTokenFilter.HEADER, tokens.issue("payment"));
                        correlationId(h);
                    })
                    .body(new CancelRequest(ORDER_TYPE, String.valueOf(orderId), reason, refundApproved))
                    .exchange((req, res) -> {
                        if (res.getStatusCode().value() == 404) return "NOT_FOUND";
                        if (res.getStatusCode().isError()) throw new RestClientException("status " + res.getStatusCode());
                        ApiResponse<Cancelled> body = res.bodyTo(new ParameterizedTypeReference<ApiResponse<Cancelled>>() {});
                        if (body == null || body.result() == null || body.result().status() == null) {
                            throw new RestClientException("empty cancel response");
                        }
                        return body.result().status();
                    });
        } catch (RestClientException e) {
            throw new Unavailable("cancel", e);
        }
    }

    private static void correlationId(HttpHeaders h) {
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (id != null) h.set(CorrelationIdFilter.HEADER, id);
    }

    record PrepareRequest(String orderType, String orderRef, long userId, int amount, List<Long> albumIds) {}

    record CancelRequest(String orderType, String orderRef, String reason, boolean refundApproved) {}

    record Cancelled(String status) {}
}
