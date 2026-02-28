package ru.aritmos.integrationbroker.security;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecuredAnnotationRule;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import ru.aritmos.integrationbroker.config.IntegrationBrokerSecurityProperties;

/**
 * Глобальное правило доступа по security.mode и anonymous policy.
 *
 * <p>Правило применяется до аннотационных правил @Secured.
 */
@Singleton
public class IntegrationBrokerSecurityRule implements SecurityRule<HttpRequest<?>> {

    private final IntegrationBrokerSecurityProperties securityProperties;
    private final SecurityModeAccessEvaluator evaluator;

    public IntegrationBrokerSecurityRule(IntegrationBrokerSecurityProperties securityProperties,
                                         SecurityModeAccessEvaluator evaluator) {
        this.securityProperties = securityProperties;
        this.evaluator = evaluator;
    }

    @Override
    public Publisher<SecurityRuleResult> check(HttpRequest<?> request, Authentication authentication) {
        String path = request == null ? null : request.getPath();
        SecurityModeAccessEvaluator.Decision decision = evaluator.evaluate(path, securityProperties);
        if (decision == SecurityModeAccessEvaluator.Decision.ALLOW) {
            return Publishers.just(SecurityRuleResult.ALLOWED);
        }
        if (authentication == null) {
            return Publishers.just(SecurityRuleResult.REJECTED);
        }
        return Publishers.just(SecurityRuleResult.UNKNOWN);
    }

    @Override
    public int getOrder() {
        return SecuredAnnotationRule.ORDER - 10;
    }
}
