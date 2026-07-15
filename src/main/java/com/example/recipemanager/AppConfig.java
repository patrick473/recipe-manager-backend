package com.example.recipemanager;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

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

    /**
     * Global CORS configuration — allows the Angular dev server and any
     * production origin to call the API.  Tighten allowed origins for
     * production deployments.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:4200", "http://localhost:3000")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }
}
