package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;

import io.micronaut.core.io.ResourceResolver;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationsHealthServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void healthReturnsUpForReachableVmAndDegradedForMissingDatabusConnector() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        RuntimeConfigStore store = new RuntimeConfigStore(new ResourceResolver(), new ObjectMapper(), null,
                "classpath:examples/sample-system-config.json", false,
                "/configuration/config/system/integrationbroker");

        RuntimeConfigStore.RuntimeConfig base = store.getEffective();
        RuntimeConfigStore.RestConnectorConfig vmConnector = new RuntimeConfigStore.RestConnectorConfig(
                baseUrl,
                new RuntimeConfigStore.RestConnectorAuth(RuntimeConfigStore.RestConnectorAuthType.NONE,
                        null, null, null, null, null, null, null, null, null, null),
                null,
                null
        );
        RuntimeConfigStore.VisitManagerIntegrationConfig vm = new RuntimeConfigStore.VisitManagerIntegrationConfig(
                true,
                "visitmanager",
                "/entrypoint/branches/{branchId}/entry-points/{entryPointId}/visits/parameters?printTicket={printTicket}",
                "/entrypoint/branches/{branchId}/services/catalog",
                "1",
                "x-entry-point-id"
        );
        RuntimeConfigStore.DataBusIntegrationConfig db = new RuntimeConfigStore.DataBusIntegrationConfig(
                true,
                "databus",
                "/databus/events/types/{type}",
                "/databus/events/types/{type}/route",
                "/databus/requests/{function}",
                "/databus/responses",
                "Service-Destination",
                "Send-To-OtherBus",
                "Send-Date",
                "Service-Sender",
                "Response-Status",
                "Response-Message",
                "integration-broker",
                false
        );

        RuntimeConfigStore.RuntimeConfig cfg = new RuntimeConfigStore.RuntimeConfig(
                base.revision(),
                base.flows(),
                base.idempotency(),
                base.inboundDlq(),
                base.keycloakProxy(),
                base.messagingOutbox(),
                base.restOutbox(),
                Map.of("visitmanager", vmConnector),
                base.crm(),
                base.medical(),
                base.appointment(),
                base.identity(),
                base.visionLabsAnalytics(),
                base.branchResolution(),
                vm,
                db
        );
        store.applyManual(cfg, "test", "test");

        IntegrationsHealthService service = new IntegrationsHealthService(store, new OAuth2ClientCredentialsService(new ObjectMapper()));

        List<IntegrationsHealthService.IntegrationHealthRow> items = service.health();
        assertEquals(2, items.size());

        IntegrationsHealthService.IntegrationHealthRow vmRow = items.get(0);
        assertEquals("VisitManager", vmRow.system());
        assertEquals("UP", vmRow.status());
        assertTrue(vmRow.latencyMs() >= 0);

        IntegrationsHealthService.IntegrationHealthRow dbRow = items.get(1);
        assertEquals("DataBus", dbRow.system());
        assertEquals("DEGRADED", dbRow.status());
        assertTrue(dbRow.details().contains("Connector not configured"));
    }
}
