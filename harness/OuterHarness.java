///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//SOURCES Agent.java Checks.java

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

// The provider's coding agent is the inner harness. This small loop is ours:
// prepare -> build -> check -> bounded repair -> report.
void main(String[] args) throws Exception {
    var root = Path.of("").toAbsolutePath();
    var mode = args.length == 0 ? "noop" : args[0].replace("--agent=", "");
    var agent = Agents.create(mode, root.resolve("bookshelf/src/main/java"));
    var task = Files.readString(root.resolve("harness/task.md"));

    // LAB 1 — feedforward: include bookshelf/approved-policy.md in the build prompt.
    var prompt = task;

    var started = System.nanoTime();
    var attempts = new ArrayList<AgentResult>();
    IO.println("build via " + mode);
    attempts.add(agent.build(prompt));
    var finding = check(root);
    show(finding);

    var repairs = 0;
    // LAB 3 — self-correction: if the check failed and the build succeeded,
    // send its diagnostic and rerun command to the agent. Permit one repair.
    // Record that attempt, check its result independently, and show the finding.

    // LAB 4 — acceptance: PASS plus no failed agent attempts. UNCHECKED,
    // failed checks, infrastructure errors, and agent failures cannot pass.
    var accepted = false;
    var elapsedMs = (System.nanoTime() - started) / 1_000_000;
    IO.println("\nstatus=" + (finding.unchecked() ? "UNCHECKED" : accepted ? "ACCEPTED" : "UNRESOLVED")
            + " repairs=" + repairs + " elapsed_ms=" + elapsedMs);
    for (var attempt : attempts) {
        IO.println("agent=" + attempt.summary() + " elapsed_ms=" + attempt.elapsedMs()
                + " input_tokens=" + AgentResult.show(attempt.inputTokens())
                + " output_tokens=" + AgentResult.show(attempt.outputTokens())
                + " cached_input_tokens=" + AgentResult.show(attempt.cachedInputTokens()));
    }
    if (!accepted) System.exit(1);
}

Finding check(Path root) {
    // LAB 2 — feedback: call Checks.run(root). The outer loop owns this check.
    return Finding.notWired();
}

void show(Finding finding) {
    IO.println("check=" + finding.state() + " elapsed_ms=" + finding.elapsedMs());
    if (finding.failed() || finding.error()) IO.println(finding.detail());
}
