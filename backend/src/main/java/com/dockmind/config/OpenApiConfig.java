package com.dockmind.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    /** A relative server URL, so "Try it out" keeps https behind the host's proxy. */
    @Bean
    OpenAPI dockMindOpenApi() {
        return new OpenAPI()
                .info(new Info().title("DockMind API").version("0.1.0")
                        .description("Purchase orders, delivery note capture and the receipt lifecycle. See CONTRACT.md."))
                .servers(List.of(new Server().url("/")));
    }
}
