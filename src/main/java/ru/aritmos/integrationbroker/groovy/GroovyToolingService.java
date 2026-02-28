package ru.aritmos.integrationbroker.groovy;

import groovy.lang.Binding;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyShell;
import groovy.lang.MissingMethodException;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Сервис tooling-операций для IDE-подобной работы с Groovy-скриптами:
 * валидация, отладочный запуск и эмуляция вызовов адаптеров.
 */
@Singleton
public class GroovyToolingService {

    public ValidationResult validate(String script) {
        if (script == null || script.isBlank()) {
            return new ValidationResult(false, List.of("Script is empty"));
        }
        try {
            new GroovyShell().parse(script);
            return new ValidationResult(true, List.of());
        } catch (Exception e) {
            return new ValidationResult(false, List.of(safeMessage(e)));
        }
    }

    public EmulationResult emulate(String script,
                                   Map<String, Object> input,
                                   Map<String, Object> meta,
                                   Map<String, Object> mocks) {
        ValidationResult validation = validate(script);
        if (!validation.valid()) {
            return EmulationResult.failed(validation.errors());
        }

        Binding binding = new Binding();
        Map<String, Object> output = new HashMap<>();
        DebugProbe debug = new DebugProbe();
        MockAliasFactory factory = new MockAliasFactory(mocks, debug);

        binding.setVariable("input", input == null ? Map.of() : input);
        binding.setVariable("meta", meta == null ? Map.of() : meta);
        binding.setVariable("output", output);
        binding.setVariable("debug", debug);

        // Эмулируем внутренние/внешние службы через динамические alias.
        binding.setVariable("crm", factory.alias("crm"));
        binding.setVariable("medical", factory.alias("medical"));
        binding.setVariable("appointment", factory.alias("appointment"));
        binding.setVariable("visit", factory.alias("visit"));
        binding.setVariable("bus", factory.alias("bus"));
        binding.setVariable("branch", factory.alias("branch"));
        binding.setVariable("rest", factory.alias("rest"));
        binding.setVariable("msg", factory.alias("msg"));
        binding.setVariable("identity", factory.alias("identity"));

        try {
            Object result = new GroovyShell(binding).evaluate(script);
            if (result instanceof Map<?, ?> mapResult) {
                for (Map.Entry<?, ?> e : mapResult.entrySet()) {
                    if (e.getKey() != null) {
                        output.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            }
            return EmulationResult.success(output, debug.records(), debug.messages());
        } catch (Exception e) {
            debug.log("Execution error: " + safeMessage(e));
            return EmulationResult.failure(output, debug.records(), debug.messages(), safeMessage(e));
        }
    }

    private static String safeMessage(Throwable e) {
        String message = e == null ? "unknown" : e.getMessage();
        return message == null ? "unknown" : message;
    }

    public record ValidationResult(boolean valid, List<String> errors) {
    }

    public record CallRecord(String alias, String method, List<Object> args, Object response) {
    }

    public record EmulationResult(boolean success,
                                  Map<String, Object> output,
                                  List<CallRecord> calls,
                                  List<String> debugMessages,
                                  List<String> errors) {
        public static EmulationResult success(Map<String, Object> output,
                                       List<CallRecord> calls,
                                       List<String> debugMessages) {
            return new EmulationResult(true, output, calls, debugMessages, List.of());
        }

        public static EmulationResult failed(List<String> errors) {
            return new EmulationResult(false, Map.of(), List.of(), List.of(), errors);
        }

        public static EmulationResult failure(Map<String, Object> output,
                                       List<CallRecord> calls,
                                       List<String> debugMessages,
                                       String error) {
            return new EmulationResult(false, output, calls, debugMessages, List.of(error));
        }
    }

    public static final class DebugProbe extends GroovyObjectSupport {
        private final List<String> messages = new ArrayList<>();
        private final List<CallRecord> records = new ArrayList<>();

        public void log(String message) {
            messages.add(Objects.toString(message, ""));
        }

        void record(String alias, String method, List<Object> args, Object response) {
            records.add(new CallRecord(alias, method, List.copyOf(args), response));
        }

        public List<String> messages() {
            return List.copyOf(messages);
        }

        public List<CallRecord> records() {
            return List.copyOf(records);
        }
    }

    static final class MockAliasFactory {
        private final Map<String, Object> mocks;
        private final DebugProbe debug;

        MockAliasFactory(Map<String, Object> mocks, DebugProbe debug) {
            this.mocks = mocks == null ? Map.of() : mocks;
            this.debug = debug;
        }

        GroovyObjectSupport alias(String name) {
            return new DynamicAlias(name, mocks, debug);
        }
    }

    static final class DynamicAlias extends GroovyObjectSupport {
        private final String alias;
        private final Map<String, Object> mocks;
        private final DebugProbe debug;

        DynamicAlias(String alias, Map<String, Object> mocks, DebugProbe debug) {
            this.alias = alias;
            this.mocks = mocks;
            this.debug = debug;
        }

        @Override
        public Object invokeMethod(String name, Object args) {
            List<Object> argList = toArgList(args);
            String key = alias + "." + name;
            Object response = mocks.getOrDefault(key, Map.of("success", true, "alias", alias, "method", name));
            debug.record(alias, name, argList, response);
            return response;
        }

        private static List<Object> toArgList(Object args) {
            if (args == null) {
                return List.of();
            }
            if (args instanceof Object[] arr) {
                List<Object> list = new ArrayList<>(arr.length);
                for (Object o : arr) {
                    list.add(o);
                }
                return list;
            }
            if (args instanceof List<?> l) {
                return new ArrayList<>(l);
            }
            if (args instanceof MissingMethodException) {
                return List.of();
            }
            return List.of(args);
        }
    }
}
