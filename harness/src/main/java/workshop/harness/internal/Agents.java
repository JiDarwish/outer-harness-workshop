package workshop.harness.internal;

import workshop.harness.Agent;
import workshop.harness.AgentResult;

import java.nio.file.Path;

/**
 * Chooses which agent the loop is driving.
 *
 * <p>The outer loop is written once and does not care which of these it got. That is
 * the point: swap the inner harness and every decision you wrote still holds.
 *
 * <p>Participants never need to open this file.
 */
public final class Agents {

    private Agents() { }

    public static Agent create(String mode, Path productionSources) {
        return switch (mode) {
            // No agent at all. The checks run against the source exactly as it stands.
            case "check-only" -> prompt -> new AgentResult(
                    "not invoked (checked existing source)", false, 0, null, null, null);
            // Deterministic scenarios, used by OuterLoopBehaviourTest.
            case "valid-first", "demo", "compile-gate", "repair-regression", "agent-failure" ->
                    new ScriptedAgent(mode, productionSources);
            // A real coding agent, if one is installed and signed in.
            case "claude", "codex" -> new CliAgent(mode, productionSources);
            default -> throw new IllegalArgumentException("Unknown agent mode: " + mode);
        };
    }
}
