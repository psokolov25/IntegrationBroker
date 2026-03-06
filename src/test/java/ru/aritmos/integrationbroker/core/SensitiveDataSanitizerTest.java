package ru.aritmos.integrationbroker.core;

import org.junit.jupiter.api.Test;

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
    void sanitizeText_shouldMaskTokensAndFlattenWhitespace() {
        String raw = "error access_token=abc123 refresh_token=rrr\nclient_secret=qwerty\tBearer zzz";
        String sanitized = SensitiveDataSanitizer.sanitizeText(raw);

        assertTrue(sanitized.contains("access_token=***"));
        assertTrue(sanitized.contains("refresh_token=***"));
        assertTrue(sanitized.contains("client_secret=***"));
        assertTrue(sanitized.contains("Bearer ***"));
        assertFalse(sanitized.contains("\n"));
        assertFalse(sanitized.contains("\t"));
    }
}
