package com.officialpapers.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Official Document Delivery Framework API")
                        .version("0.1.0")
                        .description("API for email-linked event handling, instruction management, " +
                                "chat-driven document drafting, and document export."))
                .servers(List.of(
                        new Server().url("https://api.example.com/v1").description("Production"),
                        new Server().url("https://staging-api.example.com/v1").description("Staging"),
                        new Server().url("http://localhost:8080/api/v1").description("Local Development")
                ));
    }
}
