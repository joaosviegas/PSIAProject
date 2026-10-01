package pt.psia.agent.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class LlmClient {

    private final ChatClient chatClient;

    public LlmClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * @throws RuntimeException if the model is unreachable or its reply
     *                           can't be parsed — callers fall back.
     */
    public LlmPicks pickMovies(String prompt) {
        return chatClient.prompt(prompt).call().entity(LlmPicks.class);
    }
}
