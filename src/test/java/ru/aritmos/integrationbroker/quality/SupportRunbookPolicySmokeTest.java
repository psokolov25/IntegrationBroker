package ru.aritmos.integrationbroker.quality;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportRunbookPolicySmokeTest {

    @Test
    void supportRunbook_shouldDocumentBatchCancelPolicyAndSafetyGuardrails() throws Exception {
        String runbook = Files.readString(Path.of("docs/guides/support-runbook.md"));

        assertTrue(runbook.contains("POST /admin/outbox/rest/cancel-batch"),
                "Support runbook must reference cancel-batch endpoint");
        assertTrue(runbook.contains("Политика batch-cancel для REST outbox"),
                "Support runbook must include batch-cancel policy section");
        assertTrue(runbook.contains("только для записей в статусе `QUEUED`"),
                "Batch-cancel policy must scope operation to QUEUED records");
        assertTrue(runbook.contains("фильтром `connectorId`"),
                "Batch-cancel policy must require connectorId filter in production");
        assertTrue(runbook.contains("correlationId/requestId"),
                "Batch-cancel policy must require correlation/request IDs in incident ticket");
    }
}
