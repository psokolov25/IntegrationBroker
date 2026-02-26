package ru.aritmos.integrationbroker.quality;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.core.InboundDlqService;
import ru.aritmos.integrationbroker.core.KeycloakProxyClient;
import ru.aritmos.integrationbroker.core.KeycloakProxyEnrichmentService;
import ru.aritmos.integrationbroker.core.IdempotencyService;
import ru.aritmos.integrationbroker.core.InboundProcessingService;
import ru.aritmos.integrationbroker.core.MessagingOutboxService;
import ru.aritmos.integrationbroker.core.RestOutboxService;
import ru.aritmos.integrationbroker.core.FlowEngine;
import ru.aritmos.integrationbroker.identity.IdentityModels;
import ru.aritmos.integrationbroker.identity.IdentityService;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quality-gate (эвристика) + базовые тесты движка Groovy/Idempotency.
 * <p>
 * Цель теста:
 * <ul>
 *   <li>защитить репозиторий от запрещённых практик (System.out, printStackTrace, @SuppressWarnings);</li>
 *   <li>проверить, что минимальная цепочка inbound -> flow -> idempotency работает.</li>
 * </ul>
 */
@MicronautTest
class QualityAndGroovyTest {

    @Inject
    InboundProcessingService processingService;

    @Inject
    IdempotencyService idempotencyService;

    @Inject
    RuntimeConfigStore configStore;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    InboundDlqService inboundDlqService;

    @Inject
    MessagingOutboxService messagingOutboxService;

    @Inject
    RestOutboxService restOutboxService;

    @Inject
    FlowEngine.GroovyFlowEngine groovyFlowEngine;

    @Inject
    IdentityService identityService;

    @Test
    void shouldNotContainForbiddenPatternsInMainSources() throws Exception {
        Path root = Path.of("src/main/java");
        assertTrue(Files.exists(root), "TEST_EXPECTED: отсутствует src/main/java");

        List<Path> files = Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .toList();

        assertFalse(files.isEmpty(), "TEST_EXPECTED: не найдено java-файлов в src/main/java");

        for (Path p : files) {
            String text = Files.readString(p);

            assertFalse(text.contains("System.out"), "TEST_EXPECTED: запрещён System.out: " + p);
            assertFalse(text.contains("System.err"), "TEST_EXPECTED: запрещён System.err: " + p);
            assertFalse(text.contains("printStackTrace"), "TEST_EXPECTED: запрещён printStackTrace: " + p);
            assertFalse(text.contains("@SuppressWarnings"), "TEST_EXPECTED: запрещён @SuppressWarnings: " + p);
        }
    }

    @Test
    void shouldContainSwaggerAnnotationsWithRussianText() throws Exception {
        Path root = Path.of("src/main/java");
        assertTrue(Files.exists(root), "TEST_EXPECTED: отсутствует src/main/java");

        List<Path> candidates = Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .toList();
        assertFalse(candidates.isEmpty(), "TEST_EXPECTED: не найдено java-файлов для проверки контроллеров");

        List<Path> controllers = candidates.stream()
                .filter(p -> {
                    try {
                        String t = Files.readString(p);
                        return t.contains("@Controller");
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();

        assertFalse(controllers.isEmpty(), "TEST_EXPECTED: не найдено ни одного @Controller в проекте");

        for (Path controller : controllers) {
            String text = Files.readString(controller);

            // WebSocket endpoint не является Swagger-контроллером.
            if (text.contains("@ServerWebSocket")) {
                continue;
            }

            assertTrue(text.contains("@Tag"), "TEST_EXPECTED: контроллер должен иметь @Tag: " + controller);
            assertTrue(text.contains("@Operation"), "TEST_EXPECTED: контроллер должен иметь @Operation: " + controller);

            // Эвристика: наличие кириллицы в описаниях (минимальная защита от англоязычных описаний).
            assertTrue(text.matches("(?s).*[@]Tag\\(.*[А-Яа-яЁё].*\\).*"), "TEST_EXPECTED: @Tag должен содержать русский текст: " + controller);
            assertTrue(text.matches("(?s).*[@]Operation\\(.*[А-Яа-яЁё].*\\).*"), "TEST_EXPECTED: @Operation должен содержать русский текст: " + controller);
        }
    }

    @Test
    void shouldProcessInboundAndThenSkipCompleted() throws Exception {
        InboundEnvelope env = buildEnvelope("msg-1");

        InboundProcessingService.ProcessingResult first = processingService.process(env);
        assertEquals("PROCESSED", first.outcome());
        assertNotNull(first.idempotencyKey());
        assertNotNull(first.output());
        assertTrue(first.output().containsKey("command"), "TEST_EXPECTED: flow должен сформировать output.command");

        InboundProcessingService.ProcessingResult second = processingService.process(env);
        assertEquals("SKIP_COMPLETED", second.outcome());
        assertEquals(first.idempotencyKey(), second.idempotencyKey());

        // Результат должен восстанавливаться из сохранённого result_json.
        assertTrue(second.output().containsKey("command") || second.output().containsKey("resultJson"),
                "TEST_EXPECTED: при SKIP_COMPLETED ожидается восстановленный результат");
    }

    @Test
    void shouldReturnLockedIfAlreadyInProgress() {
        RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
        InboundEnvelope env = buildEnvelope("msg-locked");

        // Создаём запись IN_PROGRESS вручную, не доводя до COMPLETED.
        IdempotencyService.IdempotencyDecision d1 = idempotencyService.decide(env, cfg.idempotency());
        assertEquals(IdempotencyService.Decision.PROCESS, d1.decision());

        InboundProcessingService.ProcessingResult res = processingService.process(env);
        assertEquals("LOCKED", res.outcome(), "TEST_EXPECTED: при IN_PROGRESS и неистёкшем lock_until ожидается LOCKED");
    }

    @Test
    void shouldStoreMessageInDlqOnFlowFailureAndSanitizeHeaders() {
        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "demo.fail",
                objectMapper.valueToTree(Map.of("hello", "world")),
                Map.of(
                        "Authorization", "Bearer SECRET-TOKEN",
                        "X-Request-Id", "req-1"
                ),
                "msg-dlq-1",
                "corr-dlq-1",
                "BR-001",
                "operator-1",
                Map.of("channel", "REST")
        );

        InboundProcessingService.StoredInDlqException ex = assertThrows(
                InboundProcessingService.StoredInDlqException.class,
                () -> processingService.process(env),
                "TEST_EXPECTED: при падении flow и включённом inboundDlq ожидается StoredInDlqException"
        );
        assertTrue(ex.dlqId() > 0, "TEST_EXPECTED: ожидается положительный dlqId");

        InboundDlqService.DlqFull full = inboundDlqService.getFull(ex.dlqId());
        assertNotNull(full, "TEST_EXPECTED: запись DLQ должна быть доступна для чтения");
        assertEquals("PENDING", full.record().status());
        assertEquals("demo.fail", full.record().type());

        // Критично: Authorization должен быть замаскирован.
        assertEquals("***", full.headers().get("Authorization"), "TEST_EXPECTED: заголовок Authorization должен быть санитизирован");
        assertEquals("req-1", full.headers().get("X-Request-Id"));
    }


    @Test
    void shouldSanitizeHeadersInMessagingOutboxOnEnqueue() {
        long id = messagingOutboxService.enqueue(
                "logging",
                "demo.topic",
                null,
                Map.of(
                        "Authorization", "Bearer SECRET-TOKEN",
                        "X-Trace-Id", "t-1"
                ),
                Map.of("hello", "world"),
                "msg-outbox-1",
                "corr-outbox-1",
                "idem-outbox-1",
                3
        );
        assertTrue(id > 0, "TEST_EXPECTED: enqueue должен вернуть положительный id");

        MessagingOutboxService.OutboxRecord rec = messagingOutboxService.get(id);
        assertNotNull(rec, "TEST_EXPECTED: запись должна читаться по id");

        Map<String, String> headers = messagingOutboxService.parseHeaders(rec.headersJson());
        assertEquals("***", headers.get("Authorization"), "TEST_EXPECTED: Authorization должен быть замаскирован при хранении outbox");
        assertEquals("t-1", headers.get("X-Trace-Id"));
    }

    @Test
    void shouldSanitizeHeadersInRestOutboxOnEnqueue() {
        long id = restOutboxService.enqueue(
                "POST",
                "http://example.local/api/demo",
                null,
                null,
                Map.of(
                        "Authorization", "Bearer SECRET-TOKEN",
                        "X-Trace-Id", "t-2"
                ),
                Map.of("a", 1),
                "idem-rest-1",
                "msg-rest-1",
                "corr-rest-1",
                "idemKey-rest-1",
                3,
                "409"
        );
        assertTrue(id > 0, "TEST_EXPECTED: enqueue должен вернуть положительный id");

        RestOutboxService.RestRecord rec = restOutboxService.get(id);
        assertNotNull(rec, "TEST_EXPECTED: запись должна читаться по id");

        Map<String, String> headers = restOutboxService.parseHeaders(rec.headersJson());
        assertEquals("***", headers.get("Authorization"), "TEST_EXPECTED: Authorization должен быть замаскирован при хранении REST outbox");
        assertEquals("t-2", headers.get("X-Trace-Id"));
    }

    @Test
    void shouldExposeUserAndPrincipalVariablesInGroovyBinding() {
        RuntimeConfigStore.FlowConfig flow = new RuntimeConfigStore.FlowConfig(
                "groovy_binding_test",
                true,
                new RuntimeConfigStore.Selector("EVENT", "binding.test"),
                Map.of("description", "Тест доступности user/principal в Groovy binding"),
                "output.principal = principal\n" +
                        "output.userId = (user == null ? null : user.id)\n" +
                        "return output\n"
        );

        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "binding.test",
                objectMapper.valueToTree(Map.of()),
                Map.of(),
                "msg-bind-1",
                "corr-bind-1",
                null,
                null,
                Map.of("channel", "TEST")
        );

        Map<String, Object> meta = Map.of(
                "principal", "operator",
                "user", Map.of("id", "U-1")
        );

        Map<String, Object> out = groovyFlowEngine.execute(env, flow, new java.util.HashMap<>(meta));
        assertEquals("operator", out.get("principal"), "TEST_EXPECTED: principal должен быть доступен в Groovy binding");
        assertEquals("U-1", out.get("userId"), "TEST_EXPECTED: user должен быть доступен в Groovy binding");
    }

    @Test
    void shouldResolveIdentityAndNormalizeSegment() {
        IdentityModels.IdentityRequest req = new IdentityModels.IdentityRequest(
                List.of(new IdentityModels.IdentityAttribute("phone", "+79990000001", Map.of())),
                Map.of("branchId", "BR-001"),
                IdentityModels.IdentityResolutionPolicy.defaultPolicy()
        );

        IdentityModels.IdentityResolution res = identityService.resolve(req, Map.of("channel", "TEST"));
        assertNotNull(res.profile(), "TEST_EXPECTED: профиль должен быть возвращён");
        assertEquals("CLIENT-001", res.profile().clientId(), "TEST_EXPECTED: ожидается clientId из static-провайдера");
        assertEquals("VIP", res.profile().segment(), "TEST_EXPECTED: VIP_CLIENT должен нормализоваться в VIP");
        assertTrue(res.profile().priorityWeight() >= 80, "TEST_EXPECTED: вес приоритета должен быть рассчитан/сохранён");
    }

    @Test
    void shouldExposeIdentityAliasInGroovyBinding() {
        RuntimeConfigStore.FlowConfig flow = new RuntimeConfigStore.FlowConfig(
                "identity_alias_test",
                true,
                new RuntimeConfigStore.Selector("EVENT", "identity.alias.test"),
                Map.of("description", "Тест доступности identity alias в Groovy binding"),
                "def res = identity.resolve([attributes:[[type:'phone', value:'+79990000001']]])\n" +
                        "output.clientId = res.profile().clientId()\n" +
                        "output.segment = res.profile().segment()\n" +
                        "return output\n"
        );

        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "identity.alias.test",
                objectMapper.valueToTree(Map.of()),
                Map.of(),
                "msg-id-alias-1",
                "corr-id-alias-1",
                "BR-001",
                null,
                Map.of("channel", "TEST")
        );

        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("branchId", "BR-001");

        Map<String, Object> out = groovyFlowEngine.execute(env, flow, meta);
        assertEquals("CLIENT-001", out.get("clientId"), "TEST_EXPECTED: identity alias должен вернуть clientId");
        assertEquals("VIP", out.get("segment"), "TEST_EXPECTED: identity alias должен вернуть нормализованный сегмент");
    }

    @Test
    void shouldExposeCrmAliasInGroovyBinding() {
        RuntimeConfigStore.FlowConfig flow = new RuntimeConfigStore.FlowConfig(
                "crm_alias_test",
                true,
                new RuntimeConfigStore.Selector("EVENT", "crm.alias.test"),
                Map.of("description", "Тест доступности crm alias в Groovy binding"),
                "def req = [keys:[[type:'phone', value:'+79990000001']]]\n" +
                        "def res = crm.findCustomer(req, meta)\n" +
                        "output.success = res.success()\n" +
                        "output.crmId = (res.success() ? res.result().crmCustomerId() : null)\n" +
                        "return output\n"
        );

        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "crm.alias.test",
                objectMapper.valueToTree(Map.of()),
                Map.of(),
                "msg-crm-alias-1",
                "corr-crm-alias-1",
                "BR-001",
                null,
                Map.of("channel", "TEST")
        );

        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("branchId", "BR-001");

        Map<String, Object> out = groovyFlowEngine.execute(env, flow, meta);
        assertEquals(Boolean.TRUE, out.get("success"), "TEST_EXPECTED: crm alias должен вернуть success=true для демо-ключа");
        assertEquals("CRM-001", out.get("crmId"), "TEST_EXPECTED: crm alias должен вернуть предопределённый CRM-идентификатор");
    }

    @Test
    void shouldStripTokensFromKeycloakProxyResponseAndNotStoreRawToken() {
        KeycloakProxyClient stub = new KeycloakProxyClient(objectMapper) {
            @Override
            public java.util.Optional<Map<String, Object>> fetchUserByToken(RuntimeConfigStore.RuntimeConfig cfg, String bearerToken) {
                return java.util.Optional.of(Map.of(
                        "id", "U-2",
                        "username", "operator2",
                        "access_token", "SHOULD_NOT_BE_PRESENT"
                ));
            }
        };
        KeycloakProxyEnrichmentService svc = new KeycloakProxyEnrichmentService(stub);

        RuntimeConfigStore.KeycloakProxyEnrichmentConfig kc = new RuntimeConfigStore.KeycloakProxyEnrichmentConfig(
                true,
                false,
                "keycloakProxy",
                List.of(RuntimeConfigStore.KeycloakProxyFetchMode.BEARER_TOKEN),
                "x-user-id",
                "Authorization",
                "/authorization/users/{userName}",
                "/authentication/userInfo",
                true,
                60,
                100,
                false,
                List.of("branchId")
        );

        RuntimeConfigStore.RuntimeConfig cfg = new RuntimeConfigStore.RuntimeConfig(
                "test",
                List.of(),
                new RuntimeConfigStore.IdempotencyConfig(true, RuntimeConfigStore.IdempotencyStrategy.AUTO, 60),
                new RuntimeConfigStore.InboundDlqConfig(false, 1, true),
                kc,
                new RuntimeConfigStore.MessagingOutboxConfig(false, "ON_FAILURE", 1, 1, 1, 1),
                new RuntimeConfigStore.RestOutboxConfig(false, "ON_FAILURE", 1, 1, 1, 1, "Idempotency-Key", "409"),
                Map.of("keycloakProxy", new RuntimeConfigStore.RestConnectorConfig("http://example", new RuntimeConfigStore.RestConnectorAuth(RuntimeConfigStore.RestConnectorAuthType.NONE, null, null, null, null, null))),
                RuntimeConfigStore.CrmConfig.disabled(),
                RuntimeConfigStore.MedicalConfig.disabled(),
                RuntimeConfigStore.AppointmentConfig.disabled(),
                RuntimeConfigStore.IdentityConfig.defaultConfig(),
                RuntimeConfigStore.VisionLabsAnalyticsConfig.disabled(),
                RuntimeConfigStore.BranchResolutionConfig.defaultConfig(),
                RuntimeConfigStore.VisitManagerIntegrationConfig.disabled(),
                RuntimeConfigStore.DataBusIntegrationConfig.disabled()
        );

        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "x",
                objectMapper.valueToTree(Map.of()),
                Map.of("Authorization", "Bearer REAL_TOKEN"),
                "m",
                "c",
                null,
                null,
                Map.of()
        );

        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        svc.enrichIfEnabled(env, cfg, meta);

        Object user = meta.get("user");
        assertTrue(user instanceof Map, "TEST_EXPECTED: meta.user должен быть Map");
        Map<?, ?> um = (Map<?, ?>) user;
        assertFalse(um.containsKey("access_token"), "TEST_EXPECTED: access_token должен быть удалён из meta.user");
    }



    @Test
    void shouldCallMedicalAliasInGroovyFlow() {
        RuntimeConfigStore.FlowConfig flow = new RuntimeConfigStore.FlowConfig(
                "medical_alias_test",
                true,
                new RuntimeConfigStore.Selector("EVENT", "medical.test"),
                Map.of("description", "Тест доступности alias medical в Groovy binding"),
                "def res = medical.getPatient([keys:[[type:'snils', value:'112-233-445 95']]], meta)\n" +
                        "output.success = res.success()\n" +
                        "output.patientId = (res.result() == null ? null : res.result().patientId())\n" +
                        "return output\n"
        );

        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "medical.test",
                objectMapper.valueToTree(Map.of()),
                Map.of(),
                "msg-med-1",
                "corr-med-1",
                "BR-001",
                "operator-1",
                Map.of("channel", "TEST")
        );

        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("branchId", "BR-001");

        Map<String, Object> out = groovyFlowEngine.execute(env, flow, meta);
        assertEquals(Boolean.TRUE, out.get("success"), "TEST_EXPECTED: medical alias должен вернуть success=true при включённом medical");
        assertNotNull(out.get("patientId"), "TEST_EXPECTED: medical alias должен вернуть patientId");
    }

    @Test
    void shouldCallAppointmentAliasInGroovyFlow() {
        RuntimeConfigStore.FlowConfig flow = new RuntimeConfigStore.FlowConfig(
                "appointment_alias_test",
                true,
                new RuntimeConfigStore.Selector("EVENT", "appointment.test"),
                Map.of("description", "Тест доступности alias appointment в Groovy binding"),
                "def res = appointment.getNearestAppointment([keys:[[type:'clientId', value:'CLIENT-001']]], meta)\n" +
                        "output.success = res.success()\n" +
                        "output.appointmentId = (res.result() == null ? null : res.result().appointmentId())\n" +
                        "return output\n"
        );

        InboundEnvelope env = new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "appointment.test",
                objectMapper.valueToTree(Map.of()),
                Map.of(),
                "msg-appt-1",
                "corr-appt-1",
                "BR-001",
                "operator-1",
                Map.of("channel", "TEST")
        );

        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("branchId", "BR-001");

        Map<String, Object> out = groovyFlowEngine.execute(env, flow, meta);
        assertEquals(Boolean.TRUE, out.get("success"), "TEST_EXPECTED: appointment alias должен вернуть success=true при включённом appointment");
        assertNotNull(out.get("appointmentId"), "TEST_EXPECTED: appointment alias должен вернуть appointmentId");
    }

    private InboundEnvelope buildEnvelope(String messageId) {
        return new InboundEnvelope(
                InboundEnvelope.Kind.EVENT,
                "visit.created",
                objectMapper.valueToTree(Map.of(
                        "ticketNumber", "A001",
                        "visitId", "V-10001"
                )),
                Map.of("source", "VisitManager"),
                messageId,
                "corr-1",
                "BR-001",
                "operator-1",
                Map.of("channel", "REST")
        );
    }

    @Test
    void shouldExposeVisitBusBranchAliasesInGroovyBinding() {
        RuntimeConfigStore.FlowConfig flow = new RuntimeConfigStore.FlowConfig(
                "groovy_aliases_test",
                true,
                new RuntimeConfigStore.Selector("EVENT", "aliases.test"),
                Map.of("description", "Тест доступности visit/bus/branch в Groovy binding"),
                "output.hasVisit = (visit != null)\n" +
                        "output.hasBus = (bus != null)\n" +
                        "output.hasBranch = (branch != null)\n" +
                        "return output\n"
        );

        InboundEnvelope env = buildEnvelope("msg-alias-1");
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("messageId", env.messageId());
        meta.put("correlationId", env.correlationId());

        Map<String, Object> out = groovyFlowEngine.execute(env, flow, meta);
        assertEquals(Boolean.TRUE, out.get("hasVisit"), "TEST_EXPECTED: alias visit должен быть доступен в Groovy");
        assertEquals(Boolean.TRUE, out.get("hasBus"), "TEST_EXPECTED: alias bus должен быть доступен в Groovy");
        assertEquals(Boolean.TRUE, out.get("hasBranch"), "TEST_EXPECTED: alias branch должен быть доступен в Groovy");
    }

}
