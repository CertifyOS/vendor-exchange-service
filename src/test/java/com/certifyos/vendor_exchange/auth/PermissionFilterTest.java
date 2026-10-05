package com.certifyos.vendor_exchange.auth;

import com.certifyos.vendor_exchange.config.VendorExchangeConfig;
import com.certifyos.vendor_exchange.http.Problem;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@SuppressWarnings("unchecked")
class PermissionFilterTest {

    /** Stand-in resource: one method per permission and one with no annotation. */
    static final class Endpoints {
        @RequiresPermission(Permission.MANAGE)
        void manage() {}

        @RequiresPermission(Permission.READ)
        void read() {}

        void open() {}
    }

    private Instance<VendorExchangeConfig> cfg;
    private VendorExchangeConfig.Permissions permissions;
    private UserContext ctx;
    private ContainerRequestContext rc;

    @BeforeEach
    void before() {
        VendorExchangeConfig mapping = Mockito.mock(VendorExchangeConfig.class);
        cfg = Mockito.mock(Instance.class);
        Mockito.when(cfg.get()).thenReturn(mapping);
        permissions = Mockito.mock(VendorExchangeConfig.Permissions.class);
        Mockito.when(mapping.permissions()).thenReturn(permissions);
        ctx = new UserContext();
        rc = Mockito.mock(ContainerRequestContext.class);
        UriInfo uri = Mockito.mock(UriInfo.class);
        Mockito.when(uri.getRequestUri()).thenReturn(URI.create("http://localhost/v1/vendor-exports/tick"));
        Mockito.when(rc.getUriInfo()).thenReturn(uri);
    }

    private static ResourceInfo resource(String name) throws NoSuchMethodException {
        Method method = Endpoints.class.getDeclaredMethod(name);
        ResourceInfo info = Mockito.mock(ResourceInfo.class);
        Mockito.when(info.getResourceMethod()).thenReturn(method);
        return info;
    }

    private static Map<String, Map<String, List<String>>> readOnlyIn(String tenant) {
        return Map.of(tenant, Map.of("vendor-export", List.of("read")));
    }

    @Test
    void enforcementOffPassesEveryone() throws NoSuchMethodException {
        Mockito.when(permissions.enforce()).thenReturn(false);
        ctx.fill("dev@certifyos.com", "org-a", readOnlyIn("org-a"));
        Assertions.assertTrue(
                new PermissionFilter(cfg, ctx).filter(rc, resource("manage")).isEmpty());
    }

    @Test
    void enforcementOnRefusesAMissingAction() throws NoSuchMethodException {
        Mockito.when(permissions.enforce()).thenReturn(true);
        ctx.fill("dev@certifyos.com", "org-a", readOnlyIn("org-a"));
        Optional<Response> refused = new PermissionFilter(cfg, ctx).filter(rc, resource("manage"));
        Assertions.assertTrue(refused.isPresent());
        Assertions.assertEquals(403, refused.get().getStatus());
        Problem problem = (Problem) refused.get().getEntity();
        Assertions.assertEquals("PERMISSION_DENIED", problem.code());
        Assertions.assertEquals("/v1/vendor-exports/tick", problem.instance());
    }

    @Test
    void enforcementOnPassesAGrantedActionForTheRequestTenantOnly() throws NoSuchMethodException {
        Mockito.when(permissions.enforce()).thenReturn(true);
        ctx.fill("dev@certifyos.com", "org-a", readOnlyIn("org-a"));
        Assertions.assertTrue(
                new PermissionFilter(cfg, ctx).filter(rc, resource("read")).isEmpty());
        ctx.fill("dev@certifyos.com", "org-b", readOnlyIn("org-a"));
        Assertions.assertTrue(
                new PermissionFilter(cfg, ctx).filter(rc, resource("read")).isPresent());
    }

    @Test
    void unannotatedMethodIsNotChecked() throws NoSuchMethodException {
        Mockito.when(permissions.enforce()).thenReturn(true);
        ctx.fill("dev@certifyos.com", "org-a", Map.of());
        Assertions.assertTrue(
                new PermissionFilter(cfg, ctx).filter(rc, resource("open")).isEmpty());
    }
}
