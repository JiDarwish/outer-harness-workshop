package workshop.harness;

import workshop.harness.internal.Agents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    // Workshop default: one repair keeps the run short. Change this to 3 (or another
    // positive limit) to allow more evidence → repair → fresh evidence cycles.
    static final int MAX_REPAIRS = 1;

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
        var reporter = RunReporter.console(mode);
        var result = run(Path.of("").toAbsolutePath(), mode, reporter);
        reporter.finished(result, MAX_REPAIRS);
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
        return run(root, mode, RunReporter.silent());
    }

    private static RunResult run(Path root, String mode, RunReporter reporter)
            throws IOException {
        var checkOnly = mode.equals("check-only");
        var agent = Agents.create(mode, root.resolve("bookshelf/src/main/java"));
        var task = taskDescription();
        var policy = Files.readString(root.resolve("bookshelf/approved-policy.md"));

        // LAB 1 — see GUIDE.md section 4. Guide before action: the approved rule has to
        // reach the agent BEFORE it writes anything. Right now it does not.
        var prompt = task;
        reporter.guidesBeforeAction(prompt, policy);

        var started = System.nanoTime();
        var attempts = new ArrayList<AttemptReport>();
        var buildLabel = checkOnly ? "current" : "build";
        reporter.attemptStarted(buildLabel);
        var build = agent.build(prompt, reporter::activity);
        reporter.candidate(build);
        var buildReport = new AttemptReport(buildLabel, prompt, build,
                checkSequence(root, build));
        attempts.add(buildReport);
        reporter.evidence(buildLabel, buildReport.findings());

        var repairs = 0;
        // LAB 2 — see GUIDE.md section 5. Repair only while the latest evidence justifies
        // it and budget remains. The budget is per attempt, not per finding.
        while (!checkOnly && needsRepair(attempts.getLast()) && repairs < MAX_REPAIRS) {
            var previous = attempts.getLast();
            repairs++;
            var repairPrompt = repairPrompt(task, policy, previous);
            reporter.repairRequested(previous, repairs, MAX_REPAIRS);
            reporter.repairStarted(repairPrompt, repairs, MAX_REPAIRS);
            var repair = agent.build(repairPrompt, reporter::activity);
            reporter.candidate(repair);
            var repairLabel = repairs == 1 ? "repair" : "repair-" + repairs;
            var repairReport = new AttemptReport(repairLabel, repairPrompt, repair,
                    checkSequence(root, repair));
            attempts.add(repairReport);
            reporter.evidence(repairLabel, repairReport.findings());
        }

        if (!checkOnly) {
            var last = attempts.getLast();
            if (needsRepair(last) && repairs >= MAX_REPAIRS) {
                reporter.budgetExhausted(repairs, MAX_REPAIRS);
            } else if (repairs == 0 || last.agent().failed()
                    || last.findings().stream().anyMatch(
                            finding -> finding.error() || finding.unchecked())) {
                reporter.noRepair(last);
            }
        }

        var unchecked = attempts.stream().flatMap(attempt -> attempt.findings().stream())
                .anyMatch(Finding::unchecked);
        var status = unchecked ? "UNCHECKED" : accepted(attempts) ? "ACCEPTED" : "UNRESOLVED";
        return new RunResult(status, repairs, (System.nanoTime() - started) / 1_000_000,
                List.copyOf(attempts));
    }

    /** The declared order is also the exact final acceptance gate. One list, two jobs. */
    static List<CheckSpec> requiredChecks() {
        return List.of(Checks.COMPILE, Checks.LINT, Checks.BUSINESS_BEHAVIOR,
                Checks.ARCHITECTURE_BOUNDARY, Checks.FULL_TEST_SUITE);
    }

    static List<Finding> checkSequence(Path root, AgentResult agent) {
        // LAB 1 — see GUIDE.md section 4. Record an outcome after EVERY agent invocation:
        //   1. If the invocation produced no candidate, SKIP every required check, with
        //      a reason. Silence is not a pass.
        //   2. Run COMPILE. On FAIL or ERROR, SKIP the dependent checks and return.
        //   3. Run LINT, BUSINESS_BEHAVIOR and ARCHITECTURE_BOUNDARY. They are
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
        // Tell the agent to preserve the checks that passed. Three failures do not earn
        // three repairs; they share this prompt.
        return task;
    }

    static boolean accepted(List<AttemptReport> reports) {
        // LAB 3 — see GUIDE.md section 6. Inspect the FINAL attempt only. It must come
        // from a successful agent call and carry a complete set of PASS findings.
        // A PASS from before a repair cannot stand in for a missing result after it.
        return false;
    }

    private static String taskDescription() throws IOException {
        try (var stream = OuterHarness.class.getResourceAsStream("/task.md")) {
            if (stream == null) throw new IOException("task.md is missing from the harness resources");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
