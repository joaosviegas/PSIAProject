package pt.psia.reco.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Fire-and-forget model load at startup. Failure is fine: the first real request will load it instead. */
@Component
public class LlmWarmup {

    private static final Logger log = LoggerFactory.getLogger(LlmWarmup.class);

    private final LlmClient llmClient;

    /**
     * Constructs a new LlmWarmup with the given LlmClient.
     *
     * @param llmClient the LlmClient to use for warming up the model
     */
    public LlmWarmup(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Warms up the LLM by making a call to it after the application is ready.
     * This method runs in a separate virtual thread and logs the outcome.
     */
    @EventListener(ApplicationReadyEvent.class)
    void warm() {
        Thread.ofVirtual().name("llm-warmup").start(() -> {
            try {
                llmClient.warmUp();
                log.info("LLM warmed up");
            } catch (Exception e) {
                log.info("LLM warm-up skipped: {}", e.getMessage());
            }
        });
    }
}
