package ru.aritmos.integrationbroker.templates;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.groovy.GroovyToolingService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

class IntegrationTemplateServiceTest {

    @Test
    void exportAndImportDryRunRoundTripShouldBeOk() {
        IntegrationTemplateService service = new IntegrationTemplateService(fakeStore(), new GroovyToolingService());
        IntegrationTemplateService.ExportResult export = service.exportTemplate(
                "branch-77", "retail", "group-a", "1.2.3", false, Map.of("segment", "vip"));

        Assertions.assertNotNull(export);
        Assertions.assertTrue(export.bytes().length > 20);
        Assertions.assertTrue(export.filename().endsWith(".ibt"));

        IntegrationTemplateService.ImportDryRunResult result = service.importDryRun(export.bytes(), "merge", "branch-77");
        Assertions.assertTrue(result.ok(), () -> String.join(" | ", result.errors()));
        Assertions.assertEquals("ibt", result.format());
        Assertions.assertTrue(result.changedArtifacts().stream().anyMatch(p -> p.endsWith("runtime/runtime-config.yml")));
        Assertions.assertTrue(result.changedArtifacts().stream().anyMatch(p -> p.endsWith("overrides/customer-overrides.yml")));
    }

    @Test
    void importDryRunShouldFailOnBrokenGroovy() {
        IntegrationTemplateService service = new IntegrationTemplateService(fakeStore(), new GroovyToolingService());
        byte[] archive = zip(Map.of(
                "manifest.yml", "format: ibt\nformatVersion: 1.0.0\nchecksums: {}\n".getBytes(StandardCharsets.UTF_8),
                "flows/f1/script.groovy", "def broken( {".getBytes(StandardCharsets.UTF_8)
        ));

        IntegrationTemplateService.ImportDryRunResult result = service.importDryRun(archive, "replace", "branch-77");

        Assertions.assertFalse(result.ok());
        Assertions.assertTrue(result.errors().stream().anyMatch(e -> e.contains("Groovy compile error")));
    }

    @Test
    void importDryRunShouldReportConflictsForKeepLocal() {
        IntegrationTemplateService service = new IntegrationTemplateService(fakeStore(), new GroovyToolingService());
        IntegrationTemplateService.ExportResult export = service.exportTemplate("branch-77", "retail", "group-a", "1.2.3", false, Map.of());

        IntegrationTemplateService.ImportDryRunResult result = service.importDryRun(export.bytes(), "keep-local", "");

        Assertions.assertTrue(result.ok());
        Assertions.assertFalse(result.conflicts().isEmpty());
        Assertions.assertTrue(result.warnings().stream().anyMatch(w -> w.contains("keep-local")));
    }

    @Test
    void importDryRunShouldFailOnInvalidArchive() {
        IntegrationTemplateService service = new IntegrationTemplateService(fakeStore(), new GroovyToolingService());

        IntegrationTemplateService.ImportDryRunResult result = service.importDryRun(
                Base64.getDecoder().decode(Base64.getEncoder().encodeToString("not-a-zip".getBytes(StandardCharsets.UTF_8))),
                "merge",
                null
        );

        Assertions.assertFalse(result.ok());
        Assertions.assertTrue(result.errors().stream().anyMatch(e -> e.contains("Unable to read archive") || e.contains("Archive has no files")));
    }

    @Test
    void importDryRunShouldFailWhenIbtHasNestedRoot() {
        IntegrationTemplateService service = new IntegrationTemplateService(fakeStore(), new GroovyToolingService());
        byte[] archive = zip(Map.of(
                "nested/manifest.yml", "format: ibt\nformatVersion: 1.0.0\nchecksums: {}\n".getBytes(StandardCharsets.UTF_8),
                "nested/runtime/runtime-config.yml", "revision: r1\n".getBytes(StandardCharsets.UTF_8)
        ));

        IntegrationTemplateService.ImportDryRunResult result = service.importDryRun(archive, "merge", "branch-77");

        Assertions.assertFalse(result.ok());
        Assertions.assertTrue(result.errors().stream().anyMatch(e -> e.contains("IBT root mismatch")));
    }

    @Test
    void importDryRunShouldFailWhenChecksumIsMissingFromManifest() {
        IntegrationTemplateService service = new IntegrationTemplateService(fakeStore(), new GroovyToolingService());
        byte[] archive = zip(Map.of(
                "manifest.yml", "format: ibt\nformatVersion: 1.0.0\nchecksums: {}\n".getBytes(StandardCharsets.UTF_8),
                "runtime/runtime-config.yml", "revision: r1\n".getBytes(StandardCharsets.UTF_8)
        ));

        IntegrationTemplateService.ImportDryRunResult result = service.importDryRun(archive, "merge", "branch-77");

        Assertions.assertFalse(result.ok());
        Assertions.assertTrue(result.errors().stream().anyMatch(e -> e.contains("Checksum is missing in manifest")));
    }

    @Test
    void importDryRunShouldWarnWhenFlowDirectoryHasNoFlowFile() {
        IntegrationTemplateService service = new IntegrationTemplateService(fakeStore(), new GroovyToolingService());
        byte[] archive = zip(Map.of(
                "manifest.yml", "format: ibt\nformatVersion: 1.0.0\nchecksums: {}\n".getBytes(StandardCharsets.UTF_8),
                "runtime/runtime-config.yml", "revision: r1\n".getBytes(StandardCharsets.UTF_8),
                "flows/f1/script.groovy", "return [ok:true]\n".getBytes(StandardCharsets.UTF_8)
        ));

        IntegrationTemplateService.ImportDryRunResult result = service.importDryRun(archive, "merge", "branch-77");

        Assertions.assertFalse(result.ok());
        Assertions.assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Flow directory has no flow.yml")));
    }

    private static RuntimeConfigStore fakeStore() {
        return new RuntimeConfigStore(null, null, null, "", false, "") {
            @Override
            public RuntimeConfig getEffective() {
                return new RuntimeConfig(
                        "rev-test",
                        List.of(new FlowConfig("flow-a", true, new Selector("EVENT", "visit.created"), Map.of("owner", "ops"), "return [status:'OK']")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }
        };
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
