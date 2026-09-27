package pt.psia.reco.service;

import java.util.List;

/**
 * Target type for ChatClient's structured-output .entity() call — Spring AI
 * generates a JSON schema from this record, appends it to the prompt, and
 * parses the model's reply back into it.
 */
public record LlmPicks(String summary, List<String> picks) {
}
