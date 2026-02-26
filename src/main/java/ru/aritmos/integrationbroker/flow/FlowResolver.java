package ru.aritmos.integrationbroker.flow;

import jakarta.inject.Singleton;
import java.util.Optional;
import ru.aritmos.integrationbroker.config.RuntimeConfigStore.RuntimeConfig;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

/**
 * Разрешение (выбор) flow по входящему сообщению.
 *
 * <p>В дальнейшем resolver будет учитывать не только {@link InboundEnvelope#kind()} и
 * {@link InboundEnvelope#type()}, но и дополнительные условия (segment/source/branchId/serviceCode,
 * appointmentType и т.д.).
 */
public interface FlowResolver {

  /**
   * Находит подходящий flow.
   *
   * @param envelope нормализованное входящее сообщение
   * @param config текущая эффективная runtime-конфигурация
   * @return найденный flow или пусто, если подходящий flow отсутствует
   */
  Optional<FlowDefinition> resolve(InboundEnvelope envelope, RuntimeConfig config);
}

/**
 * Базовая реализация {@link FlowResolver}, использующая конфигурацию {@link RuntimeConfig}.
 *
 * <p>Алгоритм первой итерации:
 *
 * <ul>
 *   <li>фильтр по enabled=true</li>
 *   <li>точное совпадение kind и type</li>
 * </ul>
 *
 * <p>Расширение (условия segment/source/branchId и т.д.) добавляется следующими итерациями без ломки
 * контракта.
 */
@Singleton
class ConfigBasedFlowResolver implements FlowResolver {

  @Override
  public Optional<FlowDefinition> resolve(InboundEnvelope envelope, RuntimeConfig config) {
    if (config == null || config.flows() == null || envelope == null) {
      return Optional.empty();
    }
    return config.flows().stream()
        .filter(f -> f != null && f.enabled())
        .filter(f -> f.selector() != null)
        .filter(f -> envelope.kind().name().equalsIgnoreCase(f.selector().kind()))
        .filter(f -> envelope.type().equals(f.selector().type()))
        .findFirst()
        .map(f -> new FlowDefinition(f.id(), f.id(), envelope.kind(), envelope.type(), null, null, f.groovy(), true));
  }
}
