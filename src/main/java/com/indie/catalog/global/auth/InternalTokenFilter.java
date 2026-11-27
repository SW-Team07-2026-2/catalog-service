package com.indie.catalog.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.catalog.global.common.exception.CommonErrorCode;
import com.indie.catalog.global.common.response.ApiResponse;
import com.indie.catalog.global.config.CatalogProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

/**
 * 내부 API의 HS256 토큰 서명, 발급자, 대상(catalog), 만료 시각을 검증합니다.
 * Gateway 수집 토큰은 본문 해시까지 확인하고 Studio 토큰은 관리자 경로만 허용합니다.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    private static final String ADMIN_PREFIX = "/internal/admin/";
    private static final int MAX_INGEST_BODY = 1024 * 1024;   // Gateway 수집 경로 한도와 같음
    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);

    private final MACVerifier verifier;
    private final CatalogProperties.Internal props;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(String sharedSecret, CatalogProperties.Internal props, Clock clock, ObjectMapper objectMapper) {
        try {
            this.verifier = new MACVerifier(sharedSecret.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("internal.shared-secret 은 32바이트 이상이어야 합니다", e);
        }
        this.props = props;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token == null || token.isBlank()) {
            reject(request, response, CommonErrorCode.INTERNAL_TOKEN_REQUIRED, "missing");
            return;
        }
        Verified verified = verify(token);
        if (verified.claims() == null) {
            reject(request, response, "expired".equals(verified.reason())
                    ? CommonErrorCode.INTERNAL_TOKEN_EXPIRED : CommonErrorCode.INTERNAL_TOKEN_INVALID, verified.reason());
            return;
        }
        JWTClaimsSet claims = verified.claims();
        if (props.ingestIssuer().equals(claims.getIssuer())) {
            ingest(request, response, chain, claims);
            return;
        }
        if (!request.getRequestURI().startsWith(ADMIN_PREFIX) || !props.adminIssuers().contains(claims.getIssuer())) {
            reject(request, response, CommonErrorCode.INTERNAL_TOKEN_INVALID, "issuer");
            return;
        }
        chain.doFilter(request, response);
    }

    /** 토큰 서명, 알고리즘, 대상과 만료 시각을 검증합니다. 발급자는 호출부에서 검사합니다. */
    private Verified verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) return Verified.rejected("alg");   // alg 고정
            if (!jwt.verify(verifier)) return Verified.rejected("signature");
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getIssuer() == null) return Verified.rejected("issuer");
            if (claims.getAudience() == null || !claims.getAudience().contains(props.expectedAudience())) return Verified.rejected("audience");
            Date exp = claims.getExpirationTime();
            if (exp == null) return Verified.rejected("exp");
            Instant now = clock.instant();
            return now.isBefore(exp.toInstant().plusSeconds(props.clockSkewSeconds())) ? new Verified(claims, null) : Verified.rejected("expired");
        } catch (Exception e) {
            return Verified.rejected("parse");
        }
    }

    /** claims 가 null 이면 reason 이 거절 사유 (expired 만 응답 코드가 다르고 나머지는 로그로만 구분) */
    private record Verified(JWTClaimsSet claims, String reason) {
        static Verified rejected(String reason) {
            return new Verified(null, reason);
        }
    }

    /** Gateway가 서명을 확인한 수집 토큰의 본문 해시를 대조합니다. 토큰을 다른 본문에 재사용할 수 없습니다. */
    private void ingest(HttpServletRequest request, HttpServletResponse response, FilterChain chain, JWTClaimsSet claims)
            throws ServletException, IOException {
        if (!props.ingestPaths().contains(request.getRequestURI())) {
            reject(request, response, CommonErrorCode.INTERNAL_TOKEN_INVALID, "ingest_path");
            return;
        }
        String sub = claims.getSubject();
        if (sub == null || !sub.startsWith("ingest:")) {
            reject(request, response, CommonErrorCode.INTERNAL_TOKEN_INVALID, "ingest_subject");
            return;
        }
        if (!(claims.getClaim("body_sha256") instanceof String expected)) {
            reject(request, response, CommonErrorCode.INTERNAL_TOKEN_INVALID, "body_sha256");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_INGEST_BODY + 1);
        if (body.length > MAX_INGEST_BODY) {
            reject(request, response, CommonErrorCode.PAYLOAD_TOO_LARGE, "body_size");
            return;
        }
        if (!MessageDigest.isEqual(sha256Hex(body).getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            reject(request, response, CommonErrorCode.BODY_DIGEST_MISMATCH, "body_digest");
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, CommonErrorCode code, String reason) throws IOException {
        log.warn("internal_token_rejected code={} reason={} path={} remote={}", code, reason, request.getRequestURI(), request.getRemoteAddr());
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.onFailure(code, code.getMessage(), null));
    }

    /** 해시를 보느라 읽은 본문을 컨트롤러(@RequestBody)가 다시 읽게 합니다 */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() { return body.length; }

        @Override
        public long getContentLengthLong() { return body.length; }
    }
}
