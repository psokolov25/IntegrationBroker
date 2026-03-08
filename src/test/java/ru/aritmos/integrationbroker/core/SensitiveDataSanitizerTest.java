package ru.aritmos.integrationbroker.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataSanitizerTest {

    @Test
    void sanitizeHeaders_shouldMaskForbiddenKeysAndBearer() {
        Map<String, String> in = Map.of(
                "Authorization", "Bearer abc.def.ghi",
                "Cookie", "sid=123",
                "X-Trace", "Bearer token-2"
        );

        Map<String, String> out = SensitiveDataSanitizer.sanitizeHeaders(in);
        assertEquals("***", out.get("Authorization"));
        assertEquals("***", out.get("Cookie"));
        assertEquals("Bearer ***", out.get("X-Trace"));
    }

    @Test
    void sanitizeHeadersForAdminProxy_shouldDropDeniedHeaders() {
        Map<String, String> in = Map.of(
                "Host", "example.org",
                "Connection", "keep-alive",
                "X-Forwarded-For", "10.0.0.1",
                "X-Correlation-Id", "corr-1",
                "Cookie", "sid=secret"
        );

        Map<String, String> out = SensitiveDataSanitizer.sanitizeHeadersForAdminProxy(in);
        assertFalse(out.containsKey("Host"));
        assertFalse(out.containsKey("Connection"));
        assertFalse(out.containsKey("X-Forwarded-For"));
        assertEquals("corr-1", out.get("X-Correlation-Id"));
        assertEquals("***", out.get("Cookie"));
    }

    @Test
    void sanitizeStructuredData_shouldMaskNestedBinaryBlobArrays() {
        Map<String, Object> payload = Map.of(
                "attachments", List.of(
                        Map.of("blob", "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo="),
                        Map.of("fileBinary", "VGhpcyBpcyBhIGJpbmFyeS1saWtlIGJhc2U2NCBwYXlsb2FkIHRoYXQgc2hvdWxkIGJlIG1hc2tlZA==")
                ),
                "meta", Map.of("note", "token access_token=abc123")
        );

        Object sanitized = SensitiveDataSanitizer.sanitizeStructuredData(payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) sanitized;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attachments = (List<Map<String, Object>>) out.get("attachments");
        assertEquals("***", attachments.get(0).get("blob"));
        assertEquals("***", attachments.get(1).get("fileBinary"));

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) out.get("meta");
        assertTrue(String.valueOf(meta.get("note")).contains("access_token=***"));
    }

    @Test
    void sanitizeText_shouldMaskTokensAndFlattenWhitespace() {
        String raw = "error access_token=abc123 refresh_token=rrr\nclient_secret=qwerty\tBearer zzz Cookie: sid=secret-session";
        String sanitized = SensitiveDataSanitizer.sanitizeText(raw);

        assertTrue(sanitized.contains("access_token=***"));
        assertTrue(sanitized.contains("refresh_token=***"));
        assertTrue(sanitized.contains("client_secret=***"));
        assertTrue(sanitized.contains("Bearer ***"));
        assertTrue(sanitized.contains("sid=***"));
        assertFalse(sanitized.contains("\n"));
        assertFalse(sanitized.contains("\t"));
    }
}
