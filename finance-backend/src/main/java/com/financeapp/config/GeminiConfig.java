package com.financeapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
public class GeminiConfig {

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Bean
    public WebClient geminiWebClient() {
        return WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("Content-Type", "application/json")
                .filter(ExchangeFilterFunction.ofRequestProcessor(req ->
                        reactor.core.publisher.Mono.just(
                                ClientRequest.from(req)
                                        .url(UriComponentsBuilder.fromUri(req.url())
                                                .queryParam("key", apiKey)
                                                .build(true).toUri())
                                        .build()
                        )
                ))
                .build();
    }
}
