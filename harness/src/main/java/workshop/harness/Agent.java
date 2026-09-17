package workshop.harness;

/**
 * The existing coding agent, viewed through the one operation the outer loop needs.
 *
 * <p>This is the whole interface. Everything a provider's agent does — reading files,
 * editing them, calling a model, retrying — happens behind this single call. The outer
 * loop does not know or care how. It only knows whether an attempt produced a candidate.
 */
public interface Agent {
    AgentResult build(String prompt);
}
