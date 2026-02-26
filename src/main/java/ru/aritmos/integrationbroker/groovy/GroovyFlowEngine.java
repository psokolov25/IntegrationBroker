package ru.aritmos.integrationbroker.groovy;

import com.fasterxml.jackson.databind.JsonNode;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.aritmos.integrationbroker.flow.FlowDefinition;

/**
 * Исполнитель Groovy-flow.
 *
 * <p>Отвечает за:
 *
 * <ul>
 *   <li>компиляцию и кеширование Groovy-скриптов</li>
 *   <li>формирование Binding (input/meta/output/ctx/beans + алиасы адаптеров)</li>
 *   <li>безопасное логирование (без утечек чувствительных данных)</li>
 * </ul>
 *
 * <p><b>Важная архитектурная оговорка:</b> Groovy здесь используется для orchestration/routing,
 * а типизированная логика интеграции должна жить в Java-адаптерах.
 */
@Singleton
public class GroovyFlowEngine {

  private static final Logger LOG = LoggerFactory.getLogger(GroovyFlowEngine.class);

  private static final ConcurrentHashMap<String, Class<? extends Script>> SCRIPT_CACHE =
      new ConcurrentHashMap<>();
  private static final AtomicLong CACHE_HITS = new AtomicLong();
  private static final AtomicLong CACHE_MISSES = new AtomicLong();

  /**
   * Выполняет flow.
   *
   * @param flow описание flow
   * @param input payload входящего envelope
   * @param meta метаданные (branchId/userId/correlationId и т.п.)
   * @param applicationContext контекст Micronaut для экспорта бинов в Groovy (переменная beans)
   * @return результат выполнения (обычно Map), готовый для сериализации
   */
  public Object execute(
      FlowDefinition flow, JsonNode input, Map<String, Object> meta, ApplicationContext applicationContext) {

    Binding binding = new Binding();
    Map<String, Object> output = new LinkedHashMap<>();

    binding.setVariable("input", input);
    binding.setVariable("meta", Optional.ofNullable(meta).orElseGet(Map::of));
    binding.setVariable("output", output);
    binding.setVariable("ctx", new FlowExecutionContext(flow.id()));

    // beans — только те DI-бины, которые помечены аннотацией @GroovyExecutable
    binding.setVariable("beans", collectExecutableBeans(applicationContext));

    // Алиасы адаптеров (пока заглушки). Полноценные типизированные адаптеры будут добавлены
    // следующими итерациями: rest/msg/crm/identity/medical/appointment
    binding.setVariable("rest", new UnsupportedAdapter("rest"));
    binding.setVariable("msg", new UnsupportedAdapter("msg"));
    binding.setVariable("crm", new UnsupportedAdapter("crm"));
    binding.setVariable("identity", new UnsupportedAdapter("identity"));
    binding.setVariable("medical", new UnsupportedAdapter("medical"));
    binding.setVariable("appointment", new UnsupportedAdapter("appointment"));

    String combined = buildCombinedScript(flow);
    String compilationKey = flow.compilationKey();

    Script script = instantiateScript(compilationKey, combined);
    script.setBinding(binding);

    LOG.debug("Выполнение flow={} (payload скрыт, meta скрыт)", flow.id());

    Object result = script.run();
    return result != null ? result : output;
  }

  /**
   * Возвращает статистику кеша компиляции Groovy.
   *
   * @return неизменяемая карта со счётчиками hits/misses/size
   */
  public Map<String, Long> getCacheStats() {
    return Collections.unmodifiableMap(
        Map.of(
            "hits", CACHE_HITS.get(),
            "misses", CACHE_MISSES.get(),
            "size", (long) SCRIPT_CACHE.size()));
  }

  private Script instantiateScript(String compilationKey, String scriptCode) {
    boolean cachedBefore = SCRIPT_CACHE.containsKey(compilationKey);
    Class<? extends Script> clazz =
        SCRIPT_CACHE.computeIfAbsent(
            compilationKey,
            key -> {
              LOG.debug("Компиляция Groovy для нового ключа (flow изменился или кеш пуст)");
              return new GroovyShell().parse(scriptCode).getClass();
            });

    if (cachedBefore) {
      CACHE_HITS.incrementAndGet();
    } else {
      CACHE_MISSES.incrementAndGet();
    }

    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("Не удалось создать экземпляр Groovy-скрипта", e);
    }
  }

  private String buildCombinedScript(FlowDefinition flow) {
    String functions = Optional.ofNullable(flow.scriptFunctions()).orElse("");
    String code = Optional.ofNullable(flow.scriptCode()).orElse("");
    if (!functions.isBlank()) {
      return functions + "\n" + code;
    }
    return code;
  }

  private Map<String, Object> collectExecutableBeans(ApplicationContext ctx) {
    if (ctx == null) {
      return Map.of();
    }
    Map<String, Object> beans = new HashMap<>();

    ctx.getAllBeanDefinitions()
        .forEach(
            def -> {
              try {
                if (!def.isSingleton()) {
                  return;
                }
                if (!def.getAnnotationMetadata().hasStereotype(GroovyExecutable.class)) {
                  return;
                }
                Object bean = ctx.getBean(def.getBeanType());
                beans.put(def.getName(), bean);
              } catch (Exception e) {
                // Безопасность: не пробрасываем stacktrace в лог, ограничиваемся сообщением
                LOG.debug("Не удалось экспортировать бин в Groovy: {}", e.getMessage());
              }
            });

    return Map.copyOf(beans);
  }

  /**
   * Контекст выполнения flow, доступный в Groovy как переменная ctx.
   *
   * <p>В первой итерации это минимальная заготовка. Следующими итерациями здесь будут:
   * publish(...) в outbox/шину, DLQ helpers, retry helpers, metrics helpers.
   */
  public static final class FlowExecutionContext {

    private final String flowId;

    public FlowExecutionContext(String flowId) {
      this.flowId = flowId;
    }

    /**
     * Возвращает id текущего flow.
     *
     * @return id flow
     */
    public String flowId() {
      return flowId;
    }

    /**
     * Заглушка publish.
     *
     * <p>Метод будет реализован вместе с outbox/messaging providers. Сейчас используется, чтобы
     * заранее зафиксировать контракт ctx и не ломать flow при расширении.
     */
    public void publish(String channel, Object message) {
      throw new UnsupportedOperationException(
          "publish(...) будет реализован после добавления outbox и messaging providers");
    }
  }

  /**
   * Маркерная аннотация для Micronaut-бинов, которые разрешено экспортировать в Groovy.
   *
   * <p>Это принципиально важно для безопасности и предсказуемости: в Groovy не должны попадать
   * «все подряд» DI-бины.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  @interface GroovyExecutable {}

  /**
   * Заглушка адаптера, чтобы flow могли ссылаться на alias-переменные (rest/msg/crm/...).
   *
   * <p>На первой итерации это осознанно «падающий» объект: он предотвращает ложное ощущение, что
   * интеграция уже реализована.
   */
  private static final class UnsupportedAdapter {
    private final String name;

    private UnsupportedAdapter(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return "UnsupportedAdapter{" + name + "}";
    }
  }
}
