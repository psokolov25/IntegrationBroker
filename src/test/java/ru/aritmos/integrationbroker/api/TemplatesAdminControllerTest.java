package ru.aritmos.integrationbroker.api;

import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.groovy.GroovyToolingService;
import ru.aritmos.integrationbroker.templates.IntegrationTemplateService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

class TemplatesAdminControllerTest {

    @Test
    void importDryRunShouldReturnErrorForInvalidBase64() {
        TemplatesAdminController controller = new TemplatesAdminController(service());

        HttpResponse<TemplatesAdminController.TemplateImportDryRunResponse> response = controller.importDryRun(
                new TemplatesAdminController.TemplateImportDryRunRequest("%%%", "merge", "branch-a"),
                null,
                null
        );

        Assertions.assertEquals(400, response.getStatus().getCode());
        TemplatesAdminController.TemplateImportDryRunResponse body = response.body();
        Assertions.assertNotNull(body);
        Assertions.assertFalse(body.ok());
        Assertions.assertTrue(body.errors().stream().anyMatch(e -> e.contains("Invalid base64 archive payload")));
        Assertions.assertNotNull(body.correlationId());
        Assertions.assertNotNull(body.requestId());
    }

    @Test
    void importDryRunShouldEchoHeaderIdsAndPopulateAudit() {
        TemplatesAdminController controller = new TemplatesAdminController(service());
        String archive = Base64.getEncoder().encodeToString(zip(Map.of(
                "manifest.yml", "format: ibt\nformatVersion: 1.0.0\nchecksums: {}\n".getBytes(StandardCharsets.UTF_8)
        )));

        HttpResponse<TemplatesAdminController.TemplateImportDryRunResponse> response = controller.importDryRun(
                new TemplatesAdminController.TemplateImportDryRunRequest(archive, "merge", "branch-a"),
                "corr-1",
                "req-1"
        );

        Assertions.assertEquals(200, response.getStatus().getCode());
        TemplatesAdminController.TemplateImportDryRunResponse body = response.body();
        Assertions.assertNotNull(body);
        Assertions.assertEquals("corr-1", body.correlationId());
        Assertions.assertEquals("req-1", body.requestId());

        TemplatesAdminController.TemplateAuditResponse audit = controller.audit(null);
        Assertions.assertFalse(audit.items().isEmpty());
        Assertions.assertEquals("corr-1", audit.items().get(0).correlationId());
        Assertions.assertEquals("req-1", audit.items().get(0).requestId());
    }

    @Test
    void auditShouldRespectLimitParameter() {
        TemplatesAdminController controller = new TemplatesAdminController(service());
        String archive = Base64.getEncoder().encodeToString(zip(Map.of(
                "manifest.yml", "format: ibt\nformatVersion: 1.0.0\nchecksums: {}\n".getBytes(StandardCharsets.UTF_8),
                "runtime/runtime-config.yml", "revision: r1\n".getBytes(StandardCharsets.UTF_8)
        )));

        controller.importDryRun(new TemplatesAdminController.TemplateImportDryRunRequest(archive, "merge", "b1"), "corr-1", "req-1");
        controller.importDryRun(new TemplatesAdminController.TemplateImportDryRunRequest(archive, "merge", "b2"), "corr-2", "req-2");

        TemplatesAdminController.TemplateAuditResponse all = controller.audit(null);
        TemplatesAdminController.TemplateAuditResponse one = controller.audit(1);

        Assertions.assertTrue(all.items().size() >= 2);
        Assertions.assertEquals(1, one.items().size());
        Assertions.assertEquals("corr-2", one.items().get(0).correlationId());
    }

    private static IntegrationTemplateService service() {
        RuntimeConfigStore store = new RuntimeConfigStore(null, null, null, "", false, "") {
            @Override
            public RuntimeConfig getEffective() {
                return new RuntimeConfig("r1",
                        List.of(new FlowConfig("f1", true, new Selector("EVENT", "visit.created"), Map.of(), "return [x:1]")),
                        null, null, null, null, null, Map.of(), null, null, null, null, null, null, null, null);
            }
        };
        return new IntegrationTemplateService(store, new GroovyToolingService());
    }

    private static byte[] zip(Map<String, byte[]> files) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                    zos.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                    zos.write(entry.getValue());
                    zos.closeEntry();
                }
            }
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
