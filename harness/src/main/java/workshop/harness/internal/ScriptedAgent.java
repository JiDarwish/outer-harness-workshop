package workshop.harness.internal;

import workshop.harness.Agent;
import workshop.harness.AgentResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A deterministic stand-in for a coding agent.
 *
 * <p>Each scenario is just an ordered list of candidate source trees: what the agent
 * "writes" on attempt one, then on attempt two if the loop asks for a repair. No model,
 * no network, no login — so every participant reaches the same checkpoint.
 *
 * <p>Participants never need to open this file.
 */
public final class ScriptedAgent implements Agent {

    private final String scenario;
    private final Path productionSources;
    private final List<Candidates> attempts;
    private int calls;

    ScriptedAgent(String scenario, Path productionSources) {
        this.scenario = scenario;
        this.productionSources = productionSources;
        this.attempts = switch (scenario) {
            // Gets it right first time. Nothing to repair.
            case "valid-first" -> List.of(Candidates.valid());
            // Three independent failures, then one repair that fixes all of them.
            case "demo" -> List.of(Candidates.violatesAllThree(), Candidates.valid());
            // Nothing compiles, so nothing downstream can run. The repair fixes it.
            case "compile-gate" -> List.of(Candidates.doesNotCompile(), Candidates.valid());
            // The repair fixes the behaviour and breaks the boundary instead.
            case "repair-regression" -> List.of(Candidates.missesSecondBorrowRule(),
                    Candidates.fixesBehaviourBreaksBoundary());
            // Produces no candidate at all.
            case "agent-failure" -> List.of();
            default -> throw new IllegalArgumentException("Unsupported scenario: " + scenario);
        };
    }

    @Override
    public AgentResult build(String prompt) {
        var started = System.nanoTime();
        calls++;
        if (attempts.isEmpty()) {
            return new AgentResult("scripted agent failure", true, elapsed(started),
                    null, null, null);
        }
        var candidate = attempts.get(Math.min(calls, attempts.size()) - 1);
        try {
            write("workshop/bookshelf/service/BorrowService.java", candidate.borrowService());
            write("workshop/bookshelf/domain/Book.java", candidate.book());
            return new AgentResult("scripted " + scenario + " attempt " + calls, false,
                    elapsed(started), null, null, null);
        } catch (IOException e) {
            return new AgentResult("scripted candidate could not be written: " + e.getMessage(),
                    true, elapsed(started), null, null, null);
        }
    }

    private void write(String relativePath, String source) throws IOException {
        var target = productionSources.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, source);
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
