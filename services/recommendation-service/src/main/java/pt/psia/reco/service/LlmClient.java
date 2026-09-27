package pt.psia.reco.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around Spring AI's ChatClient. Structured-output parsing
 * (.entity()) is handled by Spring AI: it generates a JSON schema from
 * LlmPicks, appends it to the prompt, and deserializes the model's reply.
 * Ollama connection details (base-url, model) live in application.yaml
 * under spring.ai.ollama.* — see NOTES.md.
 */
@Component
public class LlmClient {

    private final ChatClient chatClient;

    public LlmClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * @throws RuntimeException if the model is unreachable or its reply
     *                           can't be parsed into LlmPicks — callers
     *                           should catch this and fall back.
     */
    public LlmPicks pickMovies(String prompt) {
        return chatClient.prompt(prompt).call().entity(LlmPicks.class);
    }
}
