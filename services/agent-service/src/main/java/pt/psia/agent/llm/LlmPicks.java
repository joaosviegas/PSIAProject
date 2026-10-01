package pt.psia.agent.llm;

import java.util.List;

/**
 * Target type for ChatClient's structured-output .entity() call.
 */
public record LlmPicks(String summary, List<String> picks) {
}
