package pt.psia.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for setting up the ChatClient bean.
 */
@Configuration
public class ChatClientConfig {

    /** Creates and configures a ChatClient bean using the provided builder. */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
