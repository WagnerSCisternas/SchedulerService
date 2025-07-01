package com.wasc.schedulerService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        // Configurações básicas do WebClient, pode ser estendido conforme necessário
        return builder.build();
    }
}