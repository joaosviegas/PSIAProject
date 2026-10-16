package pt.psia.reco.service;

import java.util.List;

/**
 * Represents the picks returned by the LLM, including a summary and a list of recommended movie titles.
 */
public record LlmPicks(String summary, List<String> picks) {
}
