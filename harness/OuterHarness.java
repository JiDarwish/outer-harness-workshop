///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//SOURCES Agent.java Checks.java

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Keep the source snapshot's agent result and checks together across repairs. */
record AttemptReport(String label, AgentResult agent, List<Finding> findings) { }

// The provider's coding agent is the inner harness. We build the outer decision loop.
void main(String[] args) throws Exception {
    var root = Path.of("").toAbsolutePath();
    var mode = args.length == 0 ? "noop" : args[0].replace("--agent=", "");
    var agent = Agents.create(mode, root.resolve("bookshelf/src/main/java"));
    var task = Files.readString(root.resolve("harness/task.md"));
    var policy = Files.readString(root.resolve("bookshelf/approved-policy.md"));

    // LAB 1 — guide before action: include the approved policy in the build prompt.
    var prompt = task;

    var started = System.nanoTime();
    var reports = new ArrayList<AttemptReport>();
    var build = agent.build(prompt);
    reports.add(new AttemptReport("build", build, checkSequence(root, build)));
    var repairs = 0;
    // LAB 3 — one repair at most. needsRepair and repairPrompt are yours below.
    if (needsRepair(reports.getLast()) && repairs < 1) {
        repairs++;
        var repair = agent.build(repairPrompt(task, policy, reports.getLast()));
        reports.add(new AttemptReport("repair", repair, checkSequence(root, repair)));
    }

    var accepted = accepted(reports);
    for (var report : reports) show(report);
    var unchecked = reports.stream().flatMap(report -> report.findings().stream())
            .anyMatch(Finding::unchecked);
    var elapsedMs = (System.nanoTime() - started) / 1_000_000;
    IO.println("status=" + (unchecked ? "UNCHECKED" : accepted ? "ACCEPTED" : "UNRESOLVED")
            + " repairs=" + repairs + " elapsed_ms=" + elapsedMs);
    if (!accepted) System.exit(1);
}

/** The declared order also defines the exact final acceptance gate. */
List<CheckSpec> requiredChecks() {
    return List.of(Checks.COMPILE, Checks.STATIC_HYGIENE, Checks.BUSINESS_BEHAVIOR,
            Checks.ARCHITECTURE_BOUNDARY, Checks.FULL_TEST_SUITE);
}

List<Finding> checkSequence(Path root, AgentResult agent) {
    // LAB 2 — record an outcome after every agent invocation:
    // 1. If the invocation produced no candidate, SKIP every required check.
    // 2. Run COMPILE. On FAIL/ERROR, SKIP dependent checks and return.
    // 3. Run STATIC_HYGIENE, BUSINESS_BEHAVIOR, and ARCHITECTURE_BOUNDARY.
    //    They are independent: one non-PASS result does not hide the others.
    // 4. Run FULL_TEST_SUITE only if compilation and all focused checks passed.
    // Use Checks.run(root, spec) and Finding.skipped(spec, reason).
    return List.of(Finding.notWired());
}

boolean needsRepair(AttemptReport report) {
    // LAB 3 — repair application FAIL findings only. Agent failure, ERROR, and
    // UNCHECKED mean there is no trustworthy application repair to request.
    return false;
}

String repairPrompt(String task, String policy, AttemptReport report) {
    // LAB 3 — include the approved policy and ALL FAIL findings from this attempt.
    // For each: purpose, intended property, short detail, and rerun command.
    return task;
}

boolean accepted(List<AttemptReport> reports) {
    // LAB 4 — inspect the FINAL attempt only. It must come from a successful
    // agent call and contain a complete set of PASS findings. Never reuse old PASS results.
    return false;
}

/*
 * OPTIONAL LAB 5 (currently supplied): participants could implement this report
 * to expose attempt identity, timings, usage, diagnostics, skipped reasons, and
 * log paths. Keep supplied unless we decide reporting deserves workshop time.
 */
void show(AttemptReport report) {
    IO.println("attempt=" + report.label() + " agent=" + report.agent().summary()
            + " elapsed_ms=" + report.agent().elapsedMs()
            + " input_tokens=" + AgentResult.show(report.agent().inputTokens())
            + " output_tokens=" + AgentResult.show(report.agent().outputTokens())
            + " cached_input_tokens=" + AgentResult.show(report.agent().cachedInputTokens()));
    for (var finding : report.findings()) {
        IO.println("check=" + finding.name() + " state=" + finding.state()
                + " attempt=" + report.label() + " elapsed_ms=" + finding.elapsedMs());
        if (finding.failed() || finding.error()) {
            IO.println(finding.detail());
        }
        if (finding.state() == Finding.State.SKIPPED) {
            IO.println("reason=" + finding.detail());
        }
        if (finding.logPath() != null && (finding.failed() || finding.error())) {
            IO.println("full_log=" + finding.logPath());
        }
    }
}
