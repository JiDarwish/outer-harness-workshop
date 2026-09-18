package workshop.harness;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import workshop.harness.internal.Candidates;

import java.io.IOException;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Six supplied scenarios that challenge your outer-loop decisions.
 *
 * <p>Three of them end ACCEPTED and three end UNRESOLVED, and that balance is the point.
 * A loop that refuses everything passes no test worth passing. These prove yours accepts
 * correctly AND refuses correctly.
 *
 * <p>Every scenario runs against a disposable copy. Your working tree is never touched,
 * and no live model is used.
 */
class OuterLoopBehaviourTest {

    // ─── Preconditions. First show that the business check passes its control pair. ───

    @BeforeAll
    static void theBusinessCheckMustPassItsControlPair() throws Exception {
        var workspace = Workspace.create();

        if (workspace.approvedPolicy().contains("Write the outcome agreed with the librarian")) {
            fail("""
                    HARNESS VERIFICATION: STOP — replace the approved-policy placeholder first.

                    bookshelf/approved-policy.md still contains the starter questions. A
                    placeholder is not an approved expectation: nothing downstream can check
                    conformance to a rule nobody has agreed yet.""");
        }

        workspace.place(Candidates.missesSecondBorrowRule());
        if (workspace.maven("-B", "-Dtest=BorrowPolicyTest", "test").exitCode() == 0) {
            fail("""
                    HARNESS VERIFICATION: STOP — finish BorrowPolicyTest and run its control pair first.

                    Your borrowing check still passes against a known defect. Run
                    BorrowPolicyControlTest on its own to see both halves of the control pair.""");
        }
    }

    // ─── The six decision scenarios ───

    @Test
    @DisplayName("Accept a valid first attempt")
    void acceptAValidFirstAttempt() throws Exception {
        var workspace = Workspace.create();
        var result = OuterHarness.run(workspace.root(), "valid-first");

        assertEquals("ACCEPTED", result.status(), "a clean first attempt should be accepted");
        assertEquals(0, result.repairs(), "nothing failed, so nothing should have been repaired");
        for (var check : requiredCheckNames()) {
            assertEquals("PASS", result.stateOf("build", check),
                    check + " should PASS on a valid first attempt");
        }
        assertGuideReachedTheAgent(workspace, result.attempts().getFirst().prompt(),
                "The build prompt did not carry the approved policy. The agent has to receive "
                        + "the agreed rule BEFORE it writes anything (LAB 1).");
    }

    @Test
    @DisplayName("Repair all independent failures")
    void repairAllIndependentFailures() throws Exception {
        var workspace = Workspace.create();
        var result = OuterHarness.run(workspace.root(), "demo");

        assertEquals("ACCEPTED", result.status(), "the repaired source passes everything");
        assertEquals(1, result.repairs(), "three findings share ONE repair, they do not earn three");

        assertEquals("PASS", result.stateOf("build", "COMPILE"));
        for (var check : new String[] {"LINT", "BUSINESS_BEHAVIOR", "ARCHITECTURE_BOUNDARY"}) {
            assertEquals("FAIL", result.stateOf("build", check),
                    check + " must be reported independently: one FAIL cannot hide the others");
        }
        assertEquals("SKIPPED", result.stateOf("build", "FULL_TEST_SUITE"),
                "the broad suite should not run while focused checks are failing");
        for (var check : requiredCheckNames()) {
            assertEquals("PASS", result.stateOf("repair", check),
                    check + " should PASS on the repaired source");
        }

        // The prompt itself is the deliverable of LAB 2, so assert on the prompt.
        var repairPrompt = result.attempts().get(1).prompt();
        assertGuideReachedTheAgent(workspace, repairPrompt,
                "The repair prompt did not carry the approved policy.");
        for (var check : new String[] {"LINT", "BUSINESS_BEHAVIOR", "ARCHITECTURE_BOUNDARY"}) {
            assertTrue(repairPrompt.contains(check), () -> """
                    The repair prompt left out %s.

                    All three checks failed on the same attempt, so all three belong in the
                    same prompt. A repair that only mentions the first failure spends your
                    whole budget fixing a third of the problem.""".formatted(check));
        }
    }

    @Test
    @DisplayName("Stop dependent checks after compilation fails")
    void stopDependentChecksAfterCompilationFails() throws Exception {
        var workspace = Workspace.create();
        var result = OuterHarness.run(workspace.root(), "compile-gate");

        assertEquals("ACCEPTED", result.status(), "the repair fixes compilation and everything passes");
        assertEquals(1, result.repairs());

        assertEquals("FAIL", result.stateOf("build", "COMPILE"));
        for (var check : dependentCheckNames()) {
            assertEquals("SKIPPED", result.stateOf("build", check),
                    check + " cannot mean anything on source that does not compile — SKIP it, "
                            + "and say so");
            assertEquals("PASS", result.stateOf("repair", check),
                    check + " should PASS once the source compiles again");
        }
    }

    @Test
    @DisplayName("Reject a regression introduced by repair")
    void rejectARegressionIntroducedByRepair() throws Exception {
        var workspace = Workspace.create();
        var result = OuterHarness.run(workspace.root(), "repair-regression");

        assertEquals("UNRESOLVED", result.status(), """
                The repair fixed the behaviour and broke the architecture boundary in the same
                attempt. Two green labels exist, but they belong to two different versions of
                the source. Accumulating green across attempts is the easiest way to build a
                loop that accepts broken work.""");
        assertEquals(OuterHarness.MAX_REPAIRS, result.repairs(),
                "an unresolved repair should stop at the configured budget");

        assertEquals("FAIL", result.stateOf("build", "BUSINESS_BEHAVIOR"));
        assertEquals("PASS", result.stateOf("build", "ARCHITECTURE_BOUNDARY"));
        assertEquals("PASS", result.stateOf("repair", "BUSINESS_BEHAVIOR"));
        assertEquals("FAIL", result.stateOf("repair", "ARCHITECTURE_BOUNDARY"));
        assertEquals("SKIPPED", result.stateOf("repair", "FULL_TEST_SUITE"));
    }

    @Test
    @DisplayName("Stop when the agent produces no candidate")
    void stopWhenTheAgentProducesNoCandidate() throws Exception {
        var workspace = Workspace.create();
        var result = OuterHarness.run(workspace.root(), "agent-failure");

        assertEquals("UNRESOLVED", result.status());
        assertEquals(0, result.repairs(),
                "a failed agent invocation establishes no application defect, so it earns no repair");
        assertTrue(result.attempts().getFirst().agent().failed(), "the agent reported failure");
        for (var check : requiredCheckNames()) {
            assertEquals("SKIPPED", result.stateOf("build", check),
                    check + " never ran, and must say so. Silence is not a pass");
        }
    }

    @Test
    @DisplayName("Do not repair broken check infrastructure")
    void doNotRepairBrokenCheckInfrastructure() throws Exception {
        var workspace = Workspace.create();
        // Break the infrastructure honestly: the build itself can no longer be configured,
        // so no check downstream can produce a verdict about the code.
        workspace.breakTheBuildTooling();

        var result = OuterHarness.run(workspace.root(), "valid-first");

        assertEquals("UNRESOLVED", result.status());
        assertEquals(0, result.repairs());
        assertEquals("ERROR", result.stateOf("build", "COMPILE"),
                "a check that cannot produce a verdict is an ERROR, not a FAIL");
        for (var check : dependentCheckNames()) {
            assertEquals("SKIPPED", result.stateOf("build", check));
        }
        assertEquals(1, result.attempts().size(), """
                A repair was requested against a check ERROR.

                An ERROR means no verdict was produced at all. There is no application defect
                to describe, so there is nothing to ask the agent to fix — you would be
                spending your budget on a broken build tool.""");
    }

    // ─── helpers ───

    private static String[] requiredCheckNames() {
        return new String[] {"COMPILE", "LINT", "BUSINESS_BEHAVIOR",
                "ARCHITECTURE_BOUNDARY", "FULL_TEST_SUITE"};
    }

    private static String[] dependentCheckNames() {
        return new String[] {"LINT", "BUSINESS_BEHAVIOR", "ARCHITECTURE_BOUNDARY",
                "FULL_TEST_SUITE"};
    }

    /**
     * The policy may be embedded with any label or indentation, so look for its most
     * distinctive line rather than an exact format.
     */
    private static void assertGuideReachedTheAgent(Workspace workspace, String prompt,
                                                   String message) throws IOException {
        var distinctive = workspace.approvedPolicy().lines()
                .map(String::strip)
                .filter(line -> line.length() > 20 && !line.startsWith("#"))
                .max(Comparator.comparingInt(String::length))
                .orElseThrow(() -> new IOException(
                        "approved-policy.md has no substantive line to look for"));
        assertTrue(prompt.contains(distinctive), message + "\n\nLooked for: " + distinctive);
    }
}
