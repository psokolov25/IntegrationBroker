package ru.aritmos.integrationbroker.security;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRuleResult;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import ru.aritmos.integrationbroker.config.IntegrationBrokerSecurityProperties;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegrationBrokerSecurityRuleTest {

    @Test
    void shouldAllowAnonymousPathInOptionalMode() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();
        props.setMode(IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_OPTIONAL);
        props.getAnonymous().setEnabled(true);
        props.getAnonymous().setAllowPaths(List.of("/api/inbound"));

        IntegrationBrokerSecurityRule rule = new IntegrationBrokerSecurityRule(props, new SecurityModeAccessEvaluator());

        SecurityRuleResult result = single(rule.check(HttpRequest.GET("/api/inbound"), null));
        assertEquals(SecurityRuleResult.ALLOWED, result);
    }

    @Test
    void shouldRejectProtectedPathWithoutAuthentication() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();
        props.setMode(IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_REQUIRED);

        IntegrationBrokerSecurityRule rule = new IntegrationBrokerSecurityRule(props, new SecurityModeAccessEvaluator());

        SecurityRuleResult result = single(rule.check(HttpRequest.GET("/admin/idempotency"), null));
        assertEquals(SecurityRuleResult.REJECTED, result);
    }

    @Test
    void shouldDelegateToSecuredRuleWhenAuthenticationPresent() {
        IntegrationBrokerSecurityProperties props = new IntegrationBrokerSecurityProperties();
        props.setMode(IntegrationBrokerSecurityProperties.Mode.KEYCLOAK_REQUIRED);

        IntegrationBrokerSecurityRule rule = new IntegrationBrokerSecurityRule(props, new SecurityModeAccessEvaluator());

        SecurityRuleResult result = single(rule.check(HttpRequest.GET("/admin/idempotency"), Authentication.build("user", List.of("IB_ADMIN"))));
        assertEquals(SecurityRuleResult.UNKNOWN, result);
    }

    private SecurityRuleResult single(Publisher<SecurityRuleResult> publisher) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SecurityRuleResult> ref = new AtomicReference<>();

        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(SecurityRuleResult securityRuleResult) {
                ref.set(securityRuleResult);
            }

            @Override
            public void onError(Throwable t) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while waiting SecurityRule result", e);
        }
        return ref.get();
    }
}
