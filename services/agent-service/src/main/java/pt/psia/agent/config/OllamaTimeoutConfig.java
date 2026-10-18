package pt.psia.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration class for customizing the timeout settings of the Ollama API client.
 * It sets the connection and read timeouts based on the provided configuration properties.
 */
@Configuration
public class OllamaTimeoutConfig {

    /** Creates a RestClientCustomizer bean that configures the Ollama API client with specified timeout settings. */
    @Bean
    public RestClientCustomizer ollamaTimeoutCustomizer(@Value("${llm.timeout-seconds:180}") int timeoutSeconds) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }
}