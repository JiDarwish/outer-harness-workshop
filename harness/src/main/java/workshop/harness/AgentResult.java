package workshop.harness;

import java.util.List;

/**
 * What one agent invocation tells the outer loop.
 *
 * <p>Note how little there is: did it fail, how long did it take, and whatever token
 * counts the provider chose to report. A missing token count is {@code null}, which
 * means "unavailable" — never zero.
 */
public record AgentResult(String summary, boolean failed, long elapsedMs,
                          Long inputTokens, Long outputTokens, Long cachedInputTokens,
                          List<String> changedFiles) {

    public AgentResult(String summary, boolean failed, long elapsedMs,
                       Long inputTokens, Long outputTokens, Long cachedInputTokens) {
        this(summary, failed, elapsedMs, inputTokens, outputTokens, cachedInputTokens,
                List.of());
    }

    public AgentResult withChangedFiles(List<String> files) {
        return new AgentResult(summary, failed, elapsedMs, inputTokens, outputTokens,
                cachedInputTokens, List.copyOf(files));
    }

    public static String show(Long value) {
        return value == null ? "unavailable" : value.toString();
    }
}
