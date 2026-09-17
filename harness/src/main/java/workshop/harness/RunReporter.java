package workshop.harness;

import java.util.List;
import java.util.Locale;

/**
 * A terminal view of the outer loop. It observes decisions; it does not make them.
 * Tests use the silent instance so they can inspect {@link OuterHarness.RunResult}
 * without scraping console output.
 */
final class RunReporter {

    private static final String RULE = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    private final boolean visible;
    private final String mode;

    private RunReporter(boolean visible, String mode) {
        this.visible = visible;
        this.mode = mode;
    }

    static RunReporter silent() {
        return new RunReporter(false, "");
    }

    static RunReporter console(String mode) {
        return new RunReporter(true, mode);
    }

    void guidesBeforeAction(String prompt, String policy) {
        if (!visible || mode.equals("check-only")) return;
        heading("GUIDES BEFORE ACTION");
        System.out.println("Task guide:      task.md");
        System.out.println(prompt.contains(policy.strip())
                ? "Domain guide:    approved-policy.md"
                : "Domain guide:    NOT SENT — finish LAB 1");
        System.out.println("Inner harness:   " + agentName());
    }

    void attemptStarted(String label) {
        if (!visible) return;
        if (label.equals("current")) {
            heading("CURRENT SOURCE");
            System.out.println("No agent invoked; checking the source as it stands.");
            return;
        }
        heading("BUILD ATTEMPT — " + agentName().toUpperCase(Locale.ROOT));
        System.out.println(agentName() + " is working...\n");
    }

    void activity(String message) {
        if (visible) System.out.println("  " + message);
    }

    void candidate(AgentResult result) {
        if (!visible || mode.equals("check-only")) return;
        System.out.println();
        if (result.failed()) {
            System.out.println("No candidate produced: " + result.summary());
        } else {
            System.out.println("Candidate produced in " + duration(result.elapsedMs()));
            if (result.changedFiles().isEmpty()) {
                System.out.println("Changed: no production Java files");
            } else {
                System.out.println("Changed: " + String.join(", ", result.changedFiles()));
            }
        }
        if (result.inputTokens() != null || result.outputTokens() != null
                || result.cachedInputTokens() != null) {
            System.out.println("Tokens: input=" + AgentResult.show(result.inputTokens())
                    + "  output=" + AgentResult.show(result.outputTokens())
                    + "  cached_input=" + AgentResult.show(result.cachedInputTokens()));
        }
    }

    void evidence(String attemptLabel, List<Finding> findings) {
        if (!visible) return;
        var title = attemptLabel.equals("current") ? "SENSORS ON CURRENT SOURCE"
                : attemptLabel.startsWith("repair") ? "FRESH SENSORS AFTER ACTION"
                : "SENSORS AFTER ACTION";
        heading(title);
        for (var finding : findings) {
            System.out.printf("%-9s %s%n", finding.state(), finding.name());
            if (finding.failed() || finding.error()) {
                detail("Property", finding.property());
                detail("Evidence", finding.detail());
                detail("Re-run", finding.rerun());
            } else if (finding.state() == Finding.State.SKIPPED) {
                detail("Reason", finding.detail());
            }
        }
    }

    void repairRequested(AttemptReport report, int repairNumber, int maxRepairs) {
        if (!visible) return;
        heading("OUTER-HARNESS DECISION");
        var failures = report.findings().stream().filter(Finding::failed).toList();
        System.out.println(failures.size() + " application "
                + (failures.size() == 1 ? "failure was" : "failures were") + " found.");
        System.out.println("No sensor returned ERROR or UNCHECKED.");
        System.out.println("Repair budget: " + repairNumber + " of " + maxRepairs
                + " will be used.\n");
        System.out.println("Decision: request repair " + repairNumber + " of " + maxRepairs
                + ".");
    }

    void repairStarted(String repairPrompt, int repairNumber, int maxRepairs) {
        if (!visible) return;
        heading("REPAIR ATTEMPT " + repairNumber + " OF " + maxRepairs + " — "
                + agentName().toUpperCase(Locale.ROOT));
        System.out.println("\nPrompt sent to " + agentName() + ":\n");
        for (var line : repairPrompt.lines().toList()) {
            System.out.println("  " + line);
        }
        System.out.println("\n" + agentName() + " is working...\n");
    }

    void noRepair(AttemptReport report) {
        if (!visible || mode.equals("check-only")) return;
        heading("OUTER-HARNESS DECISION");
        if (report.agent().failed()) {
            System.out.println("Decision: do not request an application repair.");
            System.out.println("Reason: the agent produced no candidate to check.");
        } else if (report.findings().stream().anyMatch(Finding::error)) {
            System.out.println("Decision: do not ask the agent to repair the application.");
            System.out.println("Reason: a sensor returned ERROR and produced no verdict.");
        } else if (report.findings().stream().anyMatch(Finding::unchecked)) {
            System.out.println("Decision: do not request a repair.");
            System.out.println("Reason: the sensor sequence is still UNCHECKED.");
        } else if (report.findings().stream().noneMatch(Finding::failed)) {
            System.out.println("Decision: no repair needed.");
        } else {
            System.out.println("Decision: no repair requested.");
        }
    }

    void budgetExhausted(int repairs, int maxRepairs) {
        if (!visible) return;
        heading("OUTER-HARNESS DECISION");
        System.out.println("Decision: stop without accepting the candidate.");
        System.out.println("Reason: the latest evidence still contains an application failure,");
        System.out.println("        and the repair budget is exhausted (" + repairs + " of "
                + maxRepairs + ").");
    }

    void finished(OuterHarness.RunResult result, int maxRepairs) {
        if (!visible) return;
        heading("FINAL DECISION");
        System.out.println(result.status());
        System.out.println("Attempts: " + result.attempts().size());
        System.out.println(mode.equals("check-only")
                ? "Repairs: disabled in check-only mode"
                : "Repairs used: " + result.repairs() + " of " + maxRepairs);
        System.out.println("Elapsed: " + duration(result.elapsedMs()));
    }

    private String agentName() {
        return switch (mode) {
            case "claude" -> "Claude Code (Haiku)";
            case "codex" -> "Codex CLI (gpt-5.5)";
            default -> "coding agent";
        };
    }

    private static void heading(String title) {
        System.out.println("\n━━ " + title + " " + RULE);
    }

    private static void detail(String label, String value) {
        var lines = value.lines().toList();
        if (lines.isEmpty()) return;
        System.out.println("          " + label + ": " + lines.getFirst());
        for (var line : lines.subList(1, lines.size())) {
            System.out.println("                    " + line);
        }
    }

    private static String duration(long milliseconds) {
        return milliseconds < 1_000 ? milliseconds + "ms"
                : String.format(Locale.ROOT, "%.1fs", milliseconds / 1_000.0);
    }
}
