package ru.aritmos.integrationbroker.api;

import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.security.annotation.Secured;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminWriteEndpointsSecurityTest {

    @Test
    void adminPostPutEndpointsShouldRequireAdminRoleNotReadonly() {
        List<Class<?>> controllers = List.of(
                RuntimeConfigAdminController.class,
                OutboundAdminController.class,
                AdminIdempotencyController.class,
                AdminInboundDlqController.class,
                AdminMessagingOutboxController.class,
                AdminRestOutboxController.class
        );

        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                boolean writeEndpoint = method.getAnnotation(Post.class) != null || method.getAnnotation(Put.class) != null;
                if (!writeEndpoint) {
                    continue;
                }
                Secured secured = method.getAnnotation(Secured.class);
                if (secured == null) {
                    secured = controller.getAnnotation(Secured.class);
                }
                assertNotNull(secured, "write endpoint must be secured: " + controller.getSimpleName() + "#" + method.getName());
                List<String> roles = List.of(secured.value());
                assertTrue(roles.contains("IB_ADMIN"), "write endpoint must require IB_ADMIN: " + controller.getSimpleName() + "#" + method.getName());
                assertFalse(roles.contains("IB_READONLY"), "write endpoint must not allow IB_READONLY: " + controller.getSimpleName() + "#" + method.getName());
            }
        }
    }
}
