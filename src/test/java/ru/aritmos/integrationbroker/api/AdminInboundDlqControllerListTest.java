package ru.aritmos.integrationbroker.api;

import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.core.AdminOperationsMetrics;
import ru.aritmos.integrationbroker.core.InboundDlqService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminInboundDlqControllerListTest {

    @Test
    void list_shouldPassCorrelationIdToService() {
        CapturingDlqService dlqService = new CapturingDlqService();
        AdminInboundDlqController controller = new AdminInboundDlqController(
                dlqService,
                null,
                null,
                new AdminOperationsMetrics(),
                100
        );

        controller.list("PENDING", "visit", "crm", "B1", "operator", "corr-123", 25);

        assertEquals("corr-123", dlqService.correlationId);
        assertEquals(25, dlqService.limit);
    }

    private static final class CapturingDlqService extends InboundDlqService {
        private String correlationId;
        private int limit;

        private CapturingDlqService() {
            super(null, null);
        }

        @Override
        public List<DlqRecord> list(String status,
                                    String type,
                                    String source,
                                    String branchId,
                                    String ignoredReason,
                                    String correlationId,
                                    int limit) {
            this.correlationId = correlationId;
            this.limit = limit;
            return new ArrayList<>();
        }
    }
}
