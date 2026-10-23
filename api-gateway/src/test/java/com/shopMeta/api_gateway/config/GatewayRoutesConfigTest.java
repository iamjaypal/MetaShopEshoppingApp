package com.shopMeta.api_gateway.config;

import static com.shopMeta.api_gateway.security.AuthRequirement.ADMIN;
import static com.shopMeta.api_gateway.security.AuthRequirement.JWT;
import static com.shopMeta.api_gateway.security.AuthRequirement.METADATA_KEY;
import static com.shopMeta.api_gateway.security.AuthRequirement.PUBLIC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

/**
 * Verifies the spec 6.2 route table: each route points at the right service and carries the right
 * authorisation requirement.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class GatewayRoutesConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    private Map<String, Route> routes() {
        return routeLocator.getRoutes().collectList().block().stream()
                .collect(Collectors.toMap(Route::getId, r -> r));
    }

    @Test
    void authEndpointsArePublic() {
        Route route = routes().get("auth-service-public");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://auth-service");
        assertThat(route.getMetadata().get(METADATA_KEY)).isEqualTo(PUBLIC.name());
    }

    @Test
    void profileEndpointsRequireJwt() {
        Route route = routes().get("auth-service-profile");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://auth-service");
        assertThat(route.getMetadata().get(METADATA_KEY)).isEqualTo(JWT.name());
    }

    @Test
    void userAdministrationRequiresAdmin() {
        Route route = routes().get("auth-service-admin");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://auth-service");
        assertThat(route.getMetadata().get(METADATA_KEY)).isEqualTo(ADMIN.name());
    }

    @Test
    void catalogueIsPublicAndItsAdminSideRequiresAdmin() {
        Map<String, Route> routes = routes();

        Route admin = routes.get("product-service-admin");
        assertThat(admin).isNotNull();
        assertThat(admin.getUri().toString()).isEqualTo("lb://product-service");
        assertThat(admin.getMetadata().get(METADATA_KEY)).isEqualTo(ADMIN.name());

        Route publicRoute = routes.get("product-service-public");
        assertThat(publicRoute).isNotNull();
        assertThat(publicRoute.getUri().toString()).isEqualTo("lb://product-service");
        assertThat(publicRoute.getMetadata().get(METADATA_KEY)).isEqualTo(PUBLIC.name());
    }

    @Test
    void inventoryIsPublicAndItsAdminSideRequiresAdmin() {
        Map<String, Route> routes = routes();

        Route admin = routes.get("inventory-service-admin");
        assertThat(admin).isNotNull();
        assertThat(admin.getUri().toString()).isEqualTo("lb://inventory-service");
        assertThat(admin.getMetadata().get(METADATA_KEY)).isEqualTo(ADMIN.name());

        Route publicRoute = routes.get("inventory-service-public");
        assertThat(publicRoute).isNotNull();
        assertThat(publicRoute.getUri().toString()).isEqualTo("lb://inventory-service");
        assertThat(publicRoute.getMetadata().get(METADATA_KEY)).isEqualTo(PUBLIC.name());
    }

    @Test
    void cartRequiresJwt() {
        Route route = routes().get("cart-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://cart-service");
        assertThat(route.getMetadata().get(METADATA_KEY)).isEqualTo(JWT.name());
    }

    @Test
    void ordersRequireJwtAndTheirAdminSideRequiresAdmin() {
        Map<String, Route> routes = routes();

        Route admin = routes.get("order-service-admin");
        assertThat(admin).isNotNull();
        assertThat(admin.getUri().toString()).isEqualTo("lb://order-service");
        assertThat(admin.getMetadata().get(METADATA_KEY)).isEqualTo(ADMIN.name());

        Route orders = routes.get("order-service");
        assertThat(orders).isNotNull();
        assertThat(orders.getUri().toString()).isEqualTo("lb://order-service");
        assertThat(orders.getMetadata().get(METADATA_KEY)).isEqualTo(JWT.name());
    }

    @Test
    void internalEndpointsAreNeverRouted() {
        assertThat(routeLocator.getRoutes().collectList().block())
                .noneMatch(route -> route.getPredicate() != null
                        && route.getMetadata().containsValue("INTERNAL"))
                .allMatch(route -> !route.getId().contains("internal"));
    }

}
