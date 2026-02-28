package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OAuth2ClientCredentialsServiceTest {

    @Test
    void shouldRequestTokenAndReuseCache() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/oauth/token", exchange -> {
            calls.incrementAndGet();
            String response = "{\"access_token\":\"tok-1\",\"expires_in\":120}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        try {
            String tokenUrl = "http://localhost:" + server.getAddress().getPort() + "/oauth/token";
            RuntimeConfigStore.RestConnectorAuth auth = new RuntimeConfigStore.RestConnectorAuth(
                    RuntimeConfigStore.RestConnectorAuthType.OAUTH2_CLIENT_CREDENTIALS,
                    null,
                    null,
                    null,
                    null,
                    null,
                    tokenUrl,
                    "ib-client",
                    "ib-secret",
                    "read write",
                    null
            );

            OAuth2ClientCredentialsService service = new OAuth2ClientCredentialsService(new ObjectMapper());
            String t1 = service.resolveAccessToken(auth);
            String t2 = service.resolveAccessToken(auth);

            assertEquals("tok-1", t1);
            assertEquals("tok-1", t2);
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnNullWhenAuthConfigIncomplete() {
        OAuth2ClientCredentialsService service = new OAuth2ClientCredentialsService(new ObjectMapper());
        RuntimeConfigStore.RestConnectorAuth auth = new RuntimeConfigStore.RestConnectorAuth(
                RuntimeConfigStore.RestConnectorAuthType.OAUTH2_CLIENT_CREDENTIALS,
                null,
                null,
                null,
                null,
                null,
                null,
                "ib-client",
                "ib-secret",
                null,
                null
        );

        String token = service.resolveAccessToken(auth);
        assertEquals(null, token);
    }
}
