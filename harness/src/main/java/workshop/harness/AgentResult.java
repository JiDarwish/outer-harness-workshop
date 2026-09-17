package workshop.harness;

/**
 * What one agent invocation tells the outer loop.
 *
 * <p>Note how little there is: did it fail, how long did it take, and whatever token
 * counts the provider chose to report. A missing token count is {@code null}, which
 * means "unavailable" — never zero.
 */
public record AgentResult(String summary, boolean failed, long elapsedMs,
                          Long inputTokens, Long outputTokens, Long cachedInputTokens) {

    public static String show(Long value) {
        return value == null ? "unavailable" : value.toString();
    }
}
