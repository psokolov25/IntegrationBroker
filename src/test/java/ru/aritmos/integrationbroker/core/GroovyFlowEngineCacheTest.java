package ru.aritmos.integrationbroker.core;

import groovy.lang.Script;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroovyFlowEngineCacheTest {

    @Test
    void getOrCompile_shouldUseLruEvictionWhenCacheIsFull() {
        FlowEngine.GroovyFlowEngine.ScriptClassCache cache = new FlowEngine.GroovyFlowEngine.ScriptClassCache(2);

        Map<String, Integer> compileCounters = new HashMap<>();

        cache.getOrCompile("flow-1", code -> compile(code, compileCounters));
        cache.getOrCompile("flow-2", code -> compile(code, compileCounters));
        cache.getOrCompile("flow-2", code -> compile(code, compileCounters));
        cache.getOrCompile("flow-3", code -> compile(code, compileCounters));
        cache.getOrCompile("flow-1", code -> compile(code, compileCounters));

        assertEquals(2, compileCounters.getOrDefault("flow-1", 0));
        assertEquals(1, compileCounters.getOrDefault("flow-2", 0));
        assertEquals(1, compileCounters.getOrDefault("flow-3", 0));
    }

    @Test
    void getOrCompile_shouldCompileOnlyOnceForConcurrentRequests() throws Exception {
        FlowEngine.GroovyFlowEngine.ScriptClassCache cache = new FlowEngine.GroovyFlowEngine.ScriptClassCache(2);
        AtomicInteger compileCount = new AtomicInteger();
        CountDownLatch compilerStarted = new CountDownLatch(1);
        CountDownLatch secondReady = new CountDownLatch(1);
        CountDownLatch releaseCompile = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Class<? extends Script>> firstFuture = executor.submit(() ->
                    cache.getOrCompile("flow-1", code -> {
                        compileCount.incrementAndGet();
                        compilerStarted.countDown();
                        awaitLatch(secondReady);
                        awaitLatch(releaseCompile);
                        return ScriptOne.class;
                    })
            );

            awaitLatch(compilerStarted);

            Future<Class<? extends Script>> secondFuture = executor.submit(() -> {
                secondReady.countDown();
                return cache.getOrCompile("flow-1", code -> {
                    compileCount.incrementAndGet();
                    return ScriptTwo.class;
                });
            });

            awaitLatch(secondReady);
            releaseCompile.countDown();

            Class<? extends Script> first = firstFuture.get(5, TimeUnit.SECONDS);
            Class<? extends Script> second = secondFuture.get(5, TimeUnit.SECONDS);
            assertEquals(first, second);
            assertEquals(1, compileCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void getOrCompile_shouldPropagateCompileFailureAndAllowRetry() throws Exception {
        FlowEngine.GroovyFlowEngine.ScriptClassCache cache = new FlowEngine.GroovyFlowEngine.ScriptClassCache(2);
        AtomicInteger compileCount = new AtomicInteger();
        CountDownLatch compilerStarted = new CountDownLatch(1);
        CountDownLatch secondReady = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Class<? extends Script>> firstFuture = executor.submit(() ->
                    cache.getOrCompile("flow-error", code -> {
                        compileCount.incrementAndGet();
                        compilerStarted.countDown();
                        awaitLatch(secondReady);
                        awaitLatch(releaseFailure);
                        throw new IllegalStateException("boom");
                    })
            );

            awaitLatch(compilerStarted);

            Future<Class<? extends Script>> secondFuture = executor.submit(() -> {
                secondReady.countDown();
                return cache.getOrCompile("flow-error", code -> {
                    compileCount.incrementAndGet();
                    throw new IllegalStateException("should-not-run");
                });
            });

            awaitLatch(secondReady);
            releaseFailure.countDown();

            ExecutionException ex1 = assertThrows(ExecutionException.class, () -> firstFuture.get(5, TimeUnit.SECONDS));
            ExecutionException ex2 = assertThrows(ExecutionException.class, () -> secondFuture.get(5, TimeUnit.SECONDS));
            assertTrue(ex1.getCause() instanceof IllegalStateException);
            assertTrue(ex2.getCause() instanceof IllegalStateException);
            assertNotNull(ex1.getCause().getMessage());
            assertNotNull(ex2.getCause().getMessage());
            assertEquals(1, compileCount.get());
        } finally {
            executor.shutdownNow();
        }

        Class<? extends Script> retry = cache.getOrCompile("flow-error", code -> ScriptThree.class);
        assertEquals(ScriptThree.class, retry);
    }

    @Test
    void getOrCompile_shouldPreserveInterruptStatusWhenWaitingThreadInterrupted() throws Exception {
        FlowEngine.GroovyFlowEngine.ScriptClassCache cache = new FlowEngine.GroovyFlowEngine.ScriptClassCache(2);
        CountDownLatch compilerStarted = new CountDownLatch(1);
        CountDownLatch releaseCompile = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Class<? extends Script>> firstFuture = executor.submit(() ->
                    cache.getOrCompile("flow-interrupt", code -> {
                        compilerStarted.countDown();
                        awaitLatch(releaseCompile);
                        return ScriptOne.class;
                    })
            );

            awaitLatch(compilerStarted);

            Future<Boolean> interruptedWaiter = executor.submit(() -> {
                Thread.currentThread().interrupt();
                IllegalStateException ex = assertThrows(IllegalStateException.class,
                        () -> cache.getOrCompile("flow-interrupt", code -> ScriptTwo.class));
                assertTrue(ex.getMessage().contains("прервано"));
                return Thread.currentThread().isInterrupted();
            });

            assertTrue(interruptedWaiter.get(5, TimeUnit.SECONDS));
            releaseCompile.countDown();
            assertEquals(ScriptOne.class, firstFuture.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void getOrCompile_shouldRejectNullCompileResultAndAllowRetry() {
        FlowEngine.GroovyFlowEngine.ScriptClassCache cache = new FlowEngine.GroovyFlowEngine.ScriptClassCache(2);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cache.getOrCompile("flow-null", code -> null));
        assertEquals("Компилятор Groovy вернул null-класс", ex.getMessage());

        Class<? extends Script> retry = cache.getOrCompile("flow-null", code -> ScriptOne.class);
        assertEquals(ScriptOne.class, retry);
    }

    @Test
    void getOrCompile_shouldPropagateErrorFromCompiler() {
        FlowEngine.GroovyFlowEngine.ScriptClassCache cache = new FlowEngine.GroovyFlowEngine.ScriptClassCache(2);

        AssertionError error = assertThrows(AssertionError.class,
                () -> cache.getOrCompile("flow-error-type", code -> {
                    throw new AssertionError("fatal");
                }));
        assertEquals("fatal", error.getMessage());
    }

    @Test
    void getOrCompile_shouldRejectNullArguments() {
        FlowEngine.GroovyFlowEngine.ScriptClassCache cache = new FlowEngine.GroovyFlowEngine.ScriptClassCache(2);

        assertThrows(NullPointerException.class,
                () -> cache.getOrCompile(null, code -> ScriptOne.class));
        assertThrows(NullPointerException.class,
                () -> cache.getOrCompile("flow-1", null));
    }

    @Test
    void constructor_shouldRejectNonPositiveMaxSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new FlowEngine.GroovyFlowEngine.ScriptClassCache(0));
        assertThrows(IllegalArgumentException.class,
                () -> new FlowEngine.GroovyFlowEngine.ScriptClassCache(-1));
    }

    private static Class<? extends Script> compile(String code, Map<String, Integer> counters) {
        counters.merge(code, 1, Integer::sum);
        return switch (code) {
            case "flow-1" -> ScriptOne.class;
            case "flow-2" -> ScriptTwo.class;
            default -> ScriptThree.class;
        };
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            boolean ok = latch.await(5, TimeUnit.SECONDS);
            assertTrue(ok, "Timeout waiting for latch");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    static class ScriptOne extends Script {
        @Override
        public Object run() {
            return null;
        }
    }

    static class ScriptTwo extends Script {
        @Override
        public Object run() {
            return null;
        }
    }

    static class ScriptThree extends Script {
        @Override
        public Object run() {
            return null;
        }
    }
}
