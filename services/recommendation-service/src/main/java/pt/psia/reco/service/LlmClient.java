package pt.psia.reco.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * Client for interacting with a large language model (LLM) via the ChatClient.
 * Provides methods to pick movies based on prompts and to warm up the model.
 */
@Component
public class LlmClient {

    private final ChatClient chatClient;
    private final long timeoutSeconds;
    private final MeterRegistry registry;
    /** The executor service for running LLM calls in the background. */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "llm-call");
        t.setDaemon(true);
        return t;
    });

    /**
     * Constructs a new LlmClient with the given ChatClient, timeout, and MeterRegistry.
     *
     * @param chatClient    the ChatClient to use for interacting with the LLM
     * @param timeoutSeconds the timeout in seconds for LLM responses
     * @param registry      the MeterRegistry for recording metrics
     */
    public LlmClient(ChatClient chatClient, @Value("${llm.interactive-timeout-seconds:25}") long timeoutSeconds,
                     MeterRegistry registry) {
        this.chatClient = chatClient;
        this.timeoutSeconds = timeoutSeconds;
        this.registry = registry;
    }

    /**
     * Picks movies based on the given prompt by sending a request to the LLM.
     * The method waits for a response up to the specified timeout.
     *
     * @throws IllegalStateException if the LLM does not respond in time or if an error occurs during the call
     */
    public LlmPicks pickMovies(String prompt) {
        Future<LlmPicks> call = executor.submit(() -> chatClient.prompt(prompt).call().entity(LlmPicks.class));
        Timer.Sample sample = Timer.start(registry);
        String outcome = "error";
        try {
            LlmPicks picks = call.get(timeoutSeconds, TimeUnit.SECONDS);
            outcome = "success";
            return picks;
        } catch (TimeoutException e) {
            outcome = "timeout";
            call.cancel(true);
            throw new IllegalStateException("LLM did not answer within " + timeoutSeconds + "s", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("LLM call failed: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the LLM", e);
        } finally {
            sample.stop(Timer.builder("llm_request").tag("outcome", outcome)
                    .publishPercentileHistogram().maximumExpectedValue(Duration.ofMinutes(2)).register(registry));
        }
    }

    /** Loads the model into memory so the first real request doesn't pay for it. */
    public void warmUp() {
        chatClient.prompt("Reply with the single word: ok").call().content();
    }
}
