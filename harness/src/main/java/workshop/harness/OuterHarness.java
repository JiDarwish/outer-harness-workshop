package workshop.harness;

import workshop.harness.internal.Agents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The outer loop. This is the only file you edit today.
 *
 * <p>The provider's coding agent is the INNER harness: it reads and edits production
 * Java and reports success or failure, and nothing more. Everything around it — the
 * guide it receives, the checks that run afterwards, the repair budget, and the rule
 * for what counts as a yes — is the OUTER harness, and it is yours.
 *
 * <p>Three labs, marked below. Each one has a section in GUIDE.md.
 */
public final class OuterHarness {

    private OuterHarness() { }

    /** Everything one run of the loop decided, so a test can inspect it without parsing logs. */
    public record RunResult(String status, int repairs, long elapsedMs,
                            List<AttemptReport> attempts) {

        public boolean accepted() { return status.equals("ACCEPTED"); }

        public AttemptReport finalAttempt() { return attempts.getLast(); }

        /** The state of one named check on one attempt, for assertions. */
        public String stateOf(String attemptLabel, String checkName) {
            return attempts.stream()
                    .filter(attempt -> attempt.label().equals(attemptLabel))
                    .flatMap(attempt -> attempt.findings().stream())
                    .filter(finding -> finding.name().equals(checkName))
                    .map(finding -> finding.state().name())
                    .findFirst().orElse("ABSENT");
        }
    }

    public static void main(String[] args) throws Exception {
        var mode = args.length == 0 || args[0].equals("--check-only")
                ? "check-only"
                : args[0].replace("--agent=", "");
        var result = run(Path.of("").toAbsolutePath(), mode);
        for (var attempt : result.attempts()) show(attempt);
        System.out.println("\n=== DECISION ===");
        System.out.println(result.status());
        System.out.println("repairs=" + result.repairs() + "  elapsed="
                + formatDuration(result.elapsedMs()));
        if (!result.accepted()) System.exit(1);
    }

    /**
     * Run the loop once against the Bookshelf under {@code root}.
     *
     * <p>In "check-only" mode no agent is invoked at all and no repair is ever requested:
     * the checks run against the source exactly as it stands. That is your checkpoint
     * while you are still wiring things up.
     */
    public static RunResult run(Path root, String mode) throws IOException {
        var checkOnly = mode.equals("check-only");
        var agent = Agents.create(mode, root.resolve("bookshelf/src/main/java"));
        var task = taskDescription();
        var policy = Files.readString(root.resolve("bookshelf/approved-policy.md"));

        // LAB 1 — see GUIDE.md section 4. Guide before action: the approved rule has to
        // reach the agent BEFORE it writes anything. Right now it does not.
        var prompt = task;

        var started = System.nanoTime();
        var attempts = new ArrayList<AttemptReport>();
        var build = agent.build(prompt);
        attempts.add(new AttemptReport(checkOnly ? "current" : "build", prompt, build,
                checkSequence(root, build)));

        var repairs = 0;
        // LAB 2 — see GUIDE.md section 5. One repair at most, and only when the evidence
        // justifies one. The budget is per attempt, not per finding.
        if (!checkOnly && needsRepair(attempts.getLast()) && repairs < 1) {
            repairs++;
            var repairPrompt = repairPrompt(task, policy, attempts.getLast());
            var repair = agent.build(repairPrompt);
            attempts.add(new AttemptReport("repair", repairPrompt, repair,
                    checkSequence(root, repair)));
        }

        var unchecked = attempts.stream().flatMap(attempt -> attempt.findings().stream())
                .anyMatch(Finding::unchecked);
        var status = unchecked ? "UNCHECKED" : accepted(attempts) ? "ACCEPTED" : "UNRESOLVED";
        return new RunResult(status, repairs, (System.nanoTime() - started) / 1_000_000,
                List.copyOf(attempts));
    }

    /** The declared order is also the exact final acceptance gate. One list, two jobs. */
    static List<CheckSpec> requiredChecks() {
        return List.of(Checks.COMPILE, Checks.STATIC_HYGIENE, Checks.BUSINESS_BEHAVIOR,
                Checks.ARCHITECTURE_BOUNDARY, Checks.FULL_TEST_SUITE);
    }

    static List<Finding> checkSequence(Path root, AgentResult agent) {
        // LAB 1 — see GUIDE.md section 4. Record an outcome after EVERY agent invocation:
        //   1. If the invocation produced no candidate, SKIP every required check, with
        //      a reason. Silence is not a pass.
        //   2. Run COMPILE. On FAIL or ERROR, SKIP the dependent checks and return.
        //   3. Run STATIC_HYGIENE, BUSINESS_BEHAVIOR and ARCHITECTURE_BOUNDARY. They are
        //      independent: one non-PASS result must not hide the other two.
        //   4. Run FULL_TEST_SUITE only if compilation and all three focused checks passed.
        // Use Checks.run(root, spec) and Finding.skipped(spec, reason).
        return List.of(Finding.notWired());
    }

    static boolean needsRepair(AttemptReport report) {
        // LAB 2 — see GUIDE.md section 5. Repair application FAIL findings only.
        // An agent failure, a check ERROR, or UNCHECKED evidence means there is no
        // trustworthy application defect to repair against.
        return false;
    }

    static String repairPrompt(String task, String policy, AttemptReport report) {
        // LAB 2 — see GUIDE.md section 5. ONE prompt carrying the approved policy and
        // EVERY failing finding from this attempt. For each: the purpose, the property it
        // meant to hold, one short diagnostic line, and the exact command to reproduce it.
        // Three failures do not earn three repairs; they share this prompt.
        return task;
    }

    static boolean accepted(List<AttemptReport> reports) {
        // LAB 3 — see GUIDE.md section 6. Inspect the FINAL attempt only. It must come
        // from a successful agent call and carry a complete set of PASS findings.
        // A PASS from before a repair cannot stand in for a missing result after it.
        return false;
    }

    /** Supplied. What a reader can reconstruct afterwards without rerunning anything. */
    static void show(AttemptReport report) {
        System.out.println("\n=== " + attemptHeading(report.label()) + " ===");
        System.out.println("Agent: " + report.agent().summary());
        if (!report.label().equals("current")) {
            System.out.println("Agent time: " + formatDuration(report.agent().elapsedMs()));
            if (report.agent().inputTokens() != null || report.agent().outputTokens() != null
                    || report.agent().cachedInputTokens() != null) {
                System.out.println("Tokens: input=" + AgentResult.show(report.agent().inputTokens())
                        + "  output=" + AgentResult.show(report.agent().outputTokens())
                        + "  cached_input="
                        + AgentResult.show(report.agent().cachedInputTokens()));
            }
        }
        System.out.println();
        for (var finding : report.findings()) {
            System.out.printf("%-9s %s%n", finding.state(), finding.name());
            if (finding.failed() || finding.error()) {
                printDetail("Property", finding.property());
                printDetail("Evidence", finding.detail());
                printDetail("Re-run", finding.rerun());
            }
            if (finding.state() == Finding.State.SKIPPED) {
                printDetail("Reason", finding.detail());
            }
            if (finding.logPath() != null && (finding.failed() || finding.error())) {
                var workingDirectory = Path.of("").toAbsolutePath().normalize();
                var log = finding.logPath().toAbsolutePath().normalize();
                printDetail("Full log", log.startsWith(workingDirectory)
                        ? workingDirectory.relativize(log).toString() : log.toString());
            }
        }
    }

    private static String attemptHeading(String label) {
        return switch (label) {
            case "current" -> "CURRENT SOURCE";
            case "build" -> "BUILD ATTEMPT";
            case "repair" -> "REPAIR ATTEMPT";
            default -> label.toUpperCase();
        };
    }

    private static void printDetail(String label, String value) {
        var lines = value.lines().toList();
        if (lines.isEmpty()) return;
        System.out.println("          " + label + ": " + lines.getFirst());
        for (var line : lines.subList(1, lines.size())) {
            System.out.println("                    " + line);
        }
    }

    private static String formatDuration(long milliseconds) {
        return milliseconds < 1_000 ? milliseconds + "ms"
                : String.format(Locale.ROOT, "%.1fs", milliseconds / 1_000.0);
    }

    private static String taskDescription() throws IOException {
        try (var stream = OuterHarness.class.getResourceAsStream("/task.md")) {
            if (stream == null) throw new IOException("task.md is missing from the harness resources");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
