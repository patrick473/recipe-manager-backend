package com.example.recipemanager;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class AppConfig {

    /**
     * OpenAPI metadata for the Swagger UI and generated clients.
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Recipe Manager API")
                        .description("REST API for creating, reading, updating, and deleting Markdown-based recipes.")
                        .version("0.0.1")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT"))
                        .contact(new Contact()
                                .name("Recipe Manager")
                                .url("https://github.com/patrick473/recipe-manager-backend")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local dev"),
                        new Server().url("https://api.recipe-manager.example.com").description("Production")));
    }

}
