package ru.aritmos.integrationbroker.flow;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import ru.aritmos.integrationbroker.model.InboundEnvelope;

/**
 * Описание интеграционного flow.
 *
 * <p>Flow — минимальная единица исполнения Integration Broker. Он связывает:
 *
 * <ul>
 *   <li>условия выбора (kind/type и дополнительные условия)</li>
 *   <li>retry-политику (будет расширено следующими итерациями)</li>
 *   <li>Groovy-функции (общая библиотека)</li>
 *   <li>основной Groovy-код orchestration/routing</li>
 * </ul>
 *
 * <p>Flow-логика должна выполнять оркестрацию и маршрутизацию, но не заменять типизированный
 * Java-слой адаптеров.
 */
@Serdeable
@Schema(
    name = "FlowDefinition",
    description =
        "Описание интеграционного flow: критерии выбора и Groovy-код, выполняющий маршрутизацию/оркестрацию.")
public record FlowDefinition(
    @NotBlank
        @Schema(
            description = "Уникальный идентификатор flow (используется для ссылок, метрик и администрирования).",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "flow-visit-created")
        String id,
    @Schema(description = "Человекочитаемое имя flow.", example = "Обработка visit.created")
        String name,
    @NotNull
        @Schema(
            description = "Тип входящего сообщения, для которого предназначен flow.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        InboundEnvelope.Kind kind,
    @NotBlank
        @Schema(
            description = "Логический type сообщения (используется для выбора flow).",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "visit.created")
        String type,
    @Schema(
            description =
                "Дополнительные условия выбора flow (в первой итерации не интерпретируются ядром, "
                    + "но сохраняются в контракте для расширения: segment/source/branchId/serviceCode и т.д.).")
        Map<String, String> match,
    @Schema(
            description =
                "Библиотека Groovy-функций, которая компилируется перед основным кодом. "
                    + "Используется для переиспользования утилит внутри flow.")
        String scriptFunctions,
    @NotBlank
        @Schema(
            description = "Основной Groovy-код flow.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String scriptCode,
    @Schema(description = "Флаг включения flow.", example = "true")
        boolean enabled) {

  /**
   * Возвращает «сигнатуру» flow для кеширования компиляции.
   *
   * <p>В кеше компиляции нельзя использовать только {@link #id()}, потому что код может обновляться
   * через remote configuration, сохраняя id.
   *
   * @return строка, уникальная для данной версии кода
   */
  public String compilationKey() {
    return String.valueOf(id)
        + "|"
        + String.valueOf(scriptFunctions)
        + "|"
        + String.valueOf(scriptCode);
  }
}
