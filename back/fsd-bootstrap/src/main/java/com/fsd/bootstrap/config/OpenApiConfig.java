package com.fsd.bootstrap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dispatchFlowOpenApi(@Value("${server.port:8080}") int serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("DispatchFlow Admin API")
                        .description("Autonomous vehicle dispatch platform — admin, dispatch, fleet, and vertical APIs.")
                        .version("0.2.0")
                        .contact(new Contact().name("DispatchFlow").url("https://github.com/1634594707/DispatchFlow"))
                        .license(new License().name("Proprietary")))
                .addServersItem(new Server().url("http://localhost:" + serverPort).description("Local"));
    }
}
