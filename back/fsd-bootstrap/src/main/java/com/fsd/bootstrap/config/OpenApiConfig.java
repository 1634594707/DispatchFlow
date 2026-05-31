package com.fsd.bootstrap.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String ADMIN_TOKEN_SCHEME = "adminToken";

    @Bean
    public OpenAPI dispatchFlowOpenApi(@Value("${server.port:8080}") int serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("DispatchFlow API")
                        .description("""
                                Autonomous vehicle dispatch platform — admin console, core dispatch, fleet gateway, and open integration APIs.
                                Admin endpoints (except `/api/admin/auth/login`) require header `X-Admin-Token` from login response.
                                SSE streams accept the same token as query param `token`.
                                """)
                        .version("0.2.0")
                        .contact(new Contact().name("DispatchFlow").url("https://github.com/1634594707/DispatchFlow"))
                        .license(new License().name("Proprietary")))
                .components(new Components()
                        .addSecuritySchemes(ADMIN_TOKEN_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Admin-Token")
                                .description("Admin session token returned by POST /api/admin/auth/login")))
                .addServersItem(new Server().url("http://localhost:" + serverPort).description("Local"));
    }
}
