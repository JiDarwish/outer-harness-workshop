package workshop.harness;

import java.util.function.Consumer;

/**
 * The existing coding agent, viewed through the one operation the outer loop needs.
 *
 * <p>This is the whole interface. Everything a provider's agent does — reading files,
 * editing them, calling a model, retrying — happens behind this single call. The outer
 * loop does not know or care how. It only knows whether an attempt produced a candidate.
 */
public interface Agent {
    AgentResult build(String prompt);

    /**
     * Build while reporting a small, provider-neutral view of visible tool activity.
     * Deterministic agents have nothing useful to stream, so their existing method is
     * the default. Real CLI adapters override this overload.
     */
    default AgentResult build(String prompt, Consumer<String> activity) {
        return build(prompt);
    }
}
