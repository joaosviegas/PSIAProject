package pt.psia.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * UNVERIFIED / best-effort: Spring AI's Ollama auto-configuration doesn't
 * expose a spring.ai.ollama.* read-timeout property as of 2.0.1 (only
 * spring.ai.ollama.init.timeout, which is for model *pulling*, not chat
 * requests). This customizes the auto-configured RestClient.Builder that
 * Spring Boot's RestClientAutoConfiguration produces — which Spring AI's
 * OllamaApi is expected to pick up the same way any other auto-configured
 * RestClient consumer would. If LLM calls seem to hang instead
 * of respecting LLM_TIMEOUT_SECONDS, verify this is actually being applied
 * (log the effective timeout, or check Spring AI's OllamaAutoConfiguration
 * source for your exact version) rather than assuming it silently works.
 */
@Configuration
public class OllamaTimeoutConfig {

    @Bean
    public RestClientCustomizer ollamaTimeoutCustomizer(@Value("${llm.timeout-seconds:180}") int timeoutSeconds) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }
}