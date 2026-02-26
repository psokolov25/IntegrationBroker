package ru.aritmos.integrationbroker.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.Script;
import io.micronaut.context.BeanContext;
import jakarta.inject.Singleton;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore;
import ru.aritmos.integrationbroker.identity.IdentityModels;
import ru.aritmos.integrationbroker.identity.IdentityService;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Компоненты движка flow:
 * <ul>
 *   <li>разрешение flow (FlowResolver);</li>
 *   <li>исполнение Groovy (GroovyFlowEngine) с кешированием компиляции;</li>
 *   <li>контекст исполнения (FlowCtx) для типовых операций.</li>
 * </ul>
 * <p>
 * Важно: Groovy предназначен для orchestration/routing, но не заменяет типизированный Java-слой адаптеров.
 */
public final class FlowEngine {

    private FlowEngine() {
        // утилитарный класс
    }

    /**
     * Разрешатель flow.
     */
    public interface FlowResolver {
        /**
         * Находит flow для входящего сообщения.
         *
         * @param envelope входящее сообщение
         * @param config   effective-конфигурация
         * @return найденный flow или empty
         */
        Optional<RuntimeConfigStore.FlowConfig> resolve(InboundEnvelope envelope, RuntimeConfigStore.RuntimeConfig config);
    }

    /**
     * Базовая реализация: выбирает flow по ключу "KIND:TYPE" из runtime-конфига.
     */
    @Singleton
    public static class ConfigBasedFlowResolver implements FlowResolver {
        @Override
        public Optional<RuntimeConfigStore.FlowConfig> resolve(InboundEnvelope envelope, RuntimeConfigStore.RuntimeConfig config) {
            if (envelope == null || config == null) {
                return Optional.empty();
            }
            String key = envelope.kind() + ":" + envelope.type();
            return Optional.ofNullable(config.flowIndex().get(key));
        }
    }

    /**
     * Контекст выполнения flow.
     * <p>
     * Здесь будут появляться функции типа ctx.publish(...) и другие утилиты.
     * На этой итерации контекст оставлен минимальным и безопасным.
     */
    public static class FlowCtx {
        private final Map<String, Object> attributes = new HashMap<>();

        private final RuntimeConfigStore configStore;
        private final MessagingOutboxService messagingOutboxService;
        private final RestOutboxService restOutboxService;
        private final String sourceMessageId;
        private final String correlationId;
        private final String idempotencyKey;

        private FlowCtx(RuntimeConfigStore configStore,
                        MessagingOutboxService messagingOutboxService,
                        RestOutboxService restOutboxService,
                        String sourceMessageId,
                        String correlationId,
                        String idempotencyKey) {
            this.configStore = configStore;
            this.messagingOutboxService = messagingOutboxService;
            this.restOutboxService = restOutboxService;
            this.sourceMessageId = sourceMessageId;
            this.correlationId = correlationId;
            this.idempotencyKey = idempotencyKey;
        }

        /**
         * Сохранить произвольный атрибут в контексте.
         *
         * @param key   ключ
         * @param value значение
         */
        public void put(String key, Object value) {
            attributes.put(key, value);
        }

        /**
         * Получить атрибут из контекста.
         *
         * @param key ключ
         * @return значение или null
         */
        public Object get(String key) {
            return attributes.get(key);
        }

        /**
         * Публикация сообщения во внешний брокер через messaging outbox.
         * <p>
         * Рекомендуемый способ использования в Groovy-flow:
         * <pre>
         * {@code
         * ctx.publish("kafka", "device.commands", cmd)
         * }
         * </pre>
         *
         * @param providerId идентификатор провайдера (например, kafka/logging)
         * @param destination топик/очередь/subject
         * @param payload полезная нагрузка
         * @return id outbox-записи (0, если отправка выполнена напрямую)
         */
        public long publish(String providerId, String destination, Object payload) {
            return publish(providerId, destination, null, Map.of(), payload);
        }

        /**
         * Публикация сообщения с ключом и заголовками.
         */
        public long publish(String providerId, String destination, String messageKey, Map<String, String> headers, Object payload) {
            RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
            RuntimeConfigStore.MessagingOutboxConfig oc = cfg.messagingOutbox();
            Map<String, String> hdr = SensitiveDataSanitizer.sanitizeHeaders(headers);
            return messagingOutboxService.publish(oc,
                    providerId,
                    destination,
                    messageKey,
                    hdr,
                    payload,
                    sourceMessageId,
                    correlationId,
                    idempotencyKey);
        }

        /**
         * Выполнить REST-вызов через REST outbox.
         *
         * @param method HTTP-метод
         * @param url URL
         * @param headers заголовки
         * @param body тело
         * @return id outbox-записи (0, если вызов выполнен напрямую)
         */
        public long restCall(String method, String url, Map<String, String> headers, Object body) {
            RuntimeConfigStore.RuntimeConfig cfg = configStore.getEffective();
            RuntimeConfigStore.RestOutboxConfig oc = cfg.restOutbox();

            // Для Idempotency-Key используем вычисленный idemKey (если есть), иначе messageId.
            String idKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : sourceMessageId;

            return restOutboxService.call(
                    oc,
                    method,
                    url,
                    SensitiveDataSanitizer.sanitizeHeaders(headers),
                    body,
                    idKey,
                    sourceMessageId,
                    correlationId,
                    idempotencyKey
            );
        }

        /**
         * Выполнить REST-вызов через коннектор (рекомендуемый вариант).
         * <p>
         * Коннектор позволяет хранить секреты только в конфигурации и не сохранять их в outbox.
         *
         * @param connectorId идентификатор коннектора
         * @param method HTTP-метод
         * @param path относительный путь
         * @param headers дополнительные заголовки (будут санитизированы)
         * @param body тело
         * @return id outbox-записи (0, если вызов выполнен напрямую)
         */
        public long restCallConnector(String connectorId, String method, String path, Map<String, String> headers, Object body) {
            RuntimeConfigStore.RuntimeConfig eff = configStore.getEffective();
            RuntimeConfigStore.RestOutboxConfig oc = eff.restOutbox();

            String idKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : sourceMessageId;

            return restOutboxService.callViaConnector(
                    eff,
                    oc,
                    connectorId,
                    method,
                    path,
                    SensitiveDataSanitizer.sanitizeHeaders(headers),
                    body,
                    idKey,
                    sourceMessageId,
                    correlationId,
                    idempotencyKey
            );
        }


    }

    /**
     * Маркерная аннотация для экспорта DI-бинов в Groovy Binding.
     * <p>
     * Пример использования:
     * <pre>
     * {@code
     * @Singleton
     * @GroovyExecutable("crm")
     * public class BitrixCrmAdapter implements CrmAdapter { ... }
     * }
     * </pre>
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface GroovyExecutable {
        /**
         * Имя alias в binding (например: rest/msg/crm/identity/medical/appointment).
         */
        String value();
    }

    /**
     * Движок исполнения Groovy flow.
     * <p>
     * Реализует компиляцию и кеширование скриптов, формирование binding и безопасный дефолт
     * для неинициализированных alias-адаптеров.
     */
    @Singleton
    public static class GroovyFlowEngine {

        private static final Logger log = LoggerFactory.getLogger(GroovyFlowEngine.class);

        private final BeanContext beanContext;
        private final ObjectMapper objectMapper;
        private final int cacheMaxSize;

        private final RuntimeConfigStore configStore;
        private final MessagingOutboxService messagingOutboxService;
        private final RestOutboxService restOutboxService;
        private final IdentityService identityService;

        private final GroovyClassLoader classLoader;
        private final ConcurrentHashMap<String, Class<? extends Script>> cache = new ConcurrentHashMap<>();

        public GroovyFlowEngine(BeanContext beanContext,
                               ObjectMapper objectMapper,
                               RuntimeConfigStore configStore,
                               MessagingOutboxService messagingOutboxService,
                               RestOutboxService restOutboxService,
                               IdentityService identityService,
                               io.micronaut.context.annotation.Value("${integrationbroker.groovy.cache-max-size:200}") int cacheMaxSize) {
            this.beanContext = beanContext;
            this.objectMapper = objectMapper;
            this.cacheMaxSize = cacheMaxSize;

            this.configStore = configStore;
            this.messagingOutboxService = messagingOutboxService;
            this.restOutboxService = restOutboxService;
            this.identityService = identityService;

            CompilerConfiguration cfg = new CompilerConfiguration();
            this.classLoader = new GroovyClassLoader(GroovyFlowEngine.class.getClassLoader(), cfg);
        }

        /**
         * Выполнить flow.
         *
         * @param envelope входящее сообщение
         * @param flow     описание flow
         * @param meta     метаданные (служебные поля ядра)
         * @return результат выполнения (output)
         */
        public Map<String, Object> execute(InboundEnvelope envelope,
                                           RuntimeConfigStore.FlowConfig flow,
                                           Map<String, Object> meta) {
            if (flow == null || flow.groovy() == null) {
                throw new IllegalArgumentException("Flow не содержит Groovy-код");
            }

            Map<String, Object> output = new HashMap<>();
            FlowCtx ctx = new FlowCtx(
                    configStore,
                    messagingOutboxService,
                    restOutboxService,
                    envelope == null ? null : envelope.messageId(),
                    envelope == null ? null : envelope.correlationId(),
                    meta == null || meta.get("idempotencyKey") == null ? null : String.valueOf(meta.get("idempotencyKey"))
            );

            Map<String, Object> beans = buildBeansMap();

            Binding binding = new Binding();
            binding.setVariable("input", envelope);
            binding.setVariable("meta", meta);
            binding.setVariable("output", output);
            binding.setVariable("ctx", ctx);
            binding.setVariable("beans", beans);

            // Для удобства Groovy-flow дополнительно экспортируем user/principal как отдельные переменные.
            // Эти значения формируются на этапе enrichment (например, через KeycloakProxy) и при этом
            // не должны содержать сырых токенов.
            if (meta != null) {
                binding.setVariable("user", meta.get("user"));
                binding.setVariable("principal", meta.get("principal"));
            } else {
                binding.setVariable("user", null);
                binding.setVariable("principal", null);
            }

            // Алиасы типизированных адаптеров.
            // На старте реализуем базовые msg/rest через outbox, а остальные оставляем заглушками.
            binding.setVariable("msg", beans.getOrDefault("msg", new MsgAlias(ctx)));
            binding.setVariable("rest", beans.getOrDefault("rest", new RestAlias(ctx)));
            binding.setVariable("crm", beans.getOrDefault("crm", new AdapterAliasStub("crm")));
            binding.setVariable("identity", beans.getOrDefault("identity", new IdentityAlias(identityService, meta)));
            binding.setVariable("medical", beans.getOrDefault("medical", new AdapterAliasStub("medical")));
            binding.setVariable("appointment", beans.getOrDefault("appointment", new AdapterAliasStub("appointment")));
            binding.setVariable("visit", beans.getOrDefault("visit", new AdapterAliasStub("visit")) );
            binding.setVariable("bus", beans.getOrDefault("bus", new AdapterAliasStub("bus")) );
            binding.setVariable("branch", beans.getOrDefault("branch", new AdapterAliasStub("branch")) );

            Script script = newScript(flow.groovy());
            script.setBinding(binding);
            Object result = script.run();

            // Если скрипт вернул Map — считаем это дополнительным/переопределяющим результатом.
            if (result instanceof Map<?, ?> mapResult) {
                // Перекладываем, чтобы избежать «сырых» generics в API.
                for (Map.Entry<?, ?> e : mapResult.entrySet()) {
                    if (e.getKey() != null) {
                        output.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            }

            return output;
        }

        private Script newScript(String code) {
            if (cache.size() > cacheMaxSize) {
                // Простая эвристика: при переполнении очищаем кеш.
                // В следующих итерациях будет реализована стратегия LRU.
                cache.clear();
                log.warn("Кеш Groovy-скриптов очищен из-за превышения лимита cache-max-size={}", cacheMaxSize);
            }

            String key = sha256Hex(code);
            Class<? extends Script> compiled = cache.computeIfAbsent(key, k -> compile(code));
            try {
                return compiled.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Не удалось создать экземпляр Groovy-скрипта: " + e.getMessage(), e);
            }
        }

        private Class<? extends Script> compile(String code) {
            try {
                Class<?> clazz = classLoader.parseClass(code);
                return clazz.asSubclass(Script.class);
            } catch (Exception e) {
                // В сообщениях об ошибке не должно быть секретов — здесь их нет.
                throw new IllegalStateException("Ошибка компиляции Groovy-flow: " + e.getMessage(), e);
            }
        }

        /**
         * Собрать карту экспортируемых бинов.
         * <p>
         * Бин попадает в карту, если его класс помечен аннотацией {@link GroovyExecutable}.
         */
        private Map<String, Object> buildBeansMap() {
            Map<String, Object> map = new HashMap<>();
            for (var def : beanContext.getBeanDefinitions(Object.class)) {
                Class<?> beanType = def.getBeanType();
                GroovyExecutable ann = beanType.getAnnotation(GroovyExecutable.class);
                if (ann == null) {
                    continue;
                }
                String alias = ann.value();
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                Object bean = beanContext.getBean(beanType);
                map.put(alias, bean);
            }
            return map;
        }

        private static String sha256Hex(String input) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(digest.length * 2);
                for (byte b : digest) {
                    sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                    sb.append(Character.forDigit(b & 0xF, 16));
                }
                return sb.toString();
            } catch (Exception e) {
                throw new IllegalStateException("Не удалось вычислить SHA-256", e);
            }
        }

        /**
         * Безопасная заглушка для alias-адаптера.
         * <p>
         * Если flow пытается использовать адаптер, который не подключён (или не экспортирован),
         * выполнение должно завершиться понятной ошибкой.
         */
        private static final class AdapterAliasStub extends GroovyObjectSupport {
            private final String name;

            private AdapterAliasStub(String name) {
                this.name = name;
            }

            @Override
            public Object invokeMethod(String methodName, Object args) {
                throw new UnsupportedOperationException("Адаптер alias='" + name + "' не подключён. Попытка вызова метода: " + methodName);
            }
        }

        /**
         * Алиас msg для Groovy-flow.
         * <p>
         * Реально делегирует в {@link FlowCtx#publish(String, String, Object)}.
         */
        private static final class MsgAlias extends GroovyObjectSupport {
            private final FlowCtx ctx;

            private MsgAlias(FlowCtx ctx) {
                this.ctx = ctx;
            }

            public long publish(String providerId, String destination, Object payload) {
                return ctx.publish(providerId, destination, payload);
            }

            public long publish(String providerId, String destination, String messageKey, Map<String, String> headers, Object payload) {
                return ctx.publish(providerId, destination, messageKey, headers, payload);
            }
        }

        /**
         * Алиас rest для Groovy-flow.
         */
        private static final class RestAlias extends GroovyObjectSupport {
            private final FlowCtx ctx;

            private RestAlias(FlowCtx ctx) {
                this.ctx = ctx;
            }

            public long call(String method, String url, Map<String, String> headers, Object body) {
                return ctx.restCall(method, url, headers, body);
            }

            /**
             * Рекомендуемый вариант вызова внешнего REST через коннектор.
             * <p>
             * Позволяет не сохранять секреты/авторизацию в outbox и не протаскивать токены в сообщениях.
             */
            public long callConnector(String connectorId, String method, String path, Map<String, String> headers, Object body) {
                return ctx.restCallConnector(connectorId, method, path, headers, body);
            }
        }

        /**
         * Алиас identity для Groovy-flow.
         * <p>
         * Предоставляет доступ к слою идентификации (identity/customerIdentity) из Groovy.
         * <p>
         * Пример:
         * <pre>
         * {@code
         * def res = identity.resolve([
         *   attributes: [[type:'phone', value:'+79990000001'], [type:'email', value:'a@b.ru']]
         * ])
         * output.clientId = res.profile.clientId
         * }
         * </pre>
         */
        private static final class IdentityAlias extends GroovyObjectSupport {
            private final IdentityService identityService;
            private final Map<String, Object> meta;

            private IdentityAlias(IdentityService identityService, Map<String, Object> meta) {
                this.identityService = identityService;
                this.meta = meta;
            }

            public IdentityModels.IdentityResolution resolve(Object request) {
                IdentityModels.IdentityRequest req = identityService.convertToRequest(request);
                return identityService.resolve(req, meta == null ? Map.of() : meta);
            }
        }
    }
}
