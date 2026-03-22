package com.weatherapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI weatherApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Weather API")
                        .description("""
                                A Spring Boot REST API that returns current weather and forecast data for any city.
                                Responses are cached in Redis for 12 hours to minimise calls to the Visual Crossing
                                weather data provider.
                                """)
                        .version("1.0.0"));
    }
}
