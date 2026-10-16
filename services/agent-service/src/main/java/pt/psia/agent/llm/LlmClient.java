package pt.psia.agent.llm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * A client for the LLM service.
 */
@Component
public class LlmClient {

    private final ChatClient chatClient;
    private final MeterRegistry registry;

    /**
     * Creates a new LLM client.
     *
     * @param chatClient the chat client
     * @param registry the meter registry
     */
    public LlmClient(ChatClient chatClient, MeterRegistry registry) {
        this.chatClient = chatClient;
        this.registry = registry;
    }

    /** Sends a prompt to the LLM and returns the picks. */
    public LlmPicks pickMovies(String prompt) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "error";
        try {
            LlmPicks picks = chatClient.prompt(prompt).call().entity(LlmPicks.class);
            outcome = "success";
            return picks;
        } finally {
            sample.stop(Timer.builder("llm_request").tag("outcome", outcome)
                    .publishPercentileHistogram().maximumExpectedValue(Duration.ofMinutes(2)).register(registry));
        }
    }
}
