package pt.psia.reco.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * Thin wrapper around Spring AI's ChatClient. Structured-output parsing
 * (.entity()) is handled by Spring AI: it generates a JSON schema from
 * LlmPicks, appends it to the prompt, and deserializes the model's reply.
 * Ollama connection details (base-url, model) live in application.yaml
 * under spring.ai.ollama.*.
 *
 * Interactive calls have a hard ceiling ({@code llm.interactive-timeout-seconds}) that does
 * not depend on the HTTP client's read timeout: past it the caller falls back to the instant
 * heuristic ranking instead of leaving the user staring at a spinner.
 */
@Component
public class LlmClient {

    private final ChatClient chatClient;
    private final long timeoutSeconds;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "llm-call");
        t.setDaemon(true);
        return t;
    });

    public LlmClient(ChatClient chatClient, @Value("${llm.interactive-timeout-seconds:25}") long timeoutSeconds) {
        this.chatClient = chatClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * @throws RuntimeException if the model is unreachable, too slow, or its reply
     *                           can't be parsed into LlmPicks — callers should catch
     *                           this and fall back.
     */
    public LlmPicks pickMovies(String prompt) {
        Future<LlmPicks> call = executor.submit(() -> chatClient.prompt(prompt).call().entity(LlmPicks.class));
        try {
            return call.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new IllegalStateException("LLM did not answer within " + timeoutSeconds + "s", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("LLM call failed: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the LLM", e);
        }
    }

    /** Loads the model into memory so the first real request doesn't pay for it. */
    public void warmUp() {
        chatClient.prompt("Reply with the single word: ok").call().content();
    }
}
