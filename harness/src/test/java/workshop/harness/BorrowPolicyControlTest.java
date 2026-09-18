package workshop.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import workshop.harness.internal.Candidates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Checks YOUR borrowing check before anything downstream is allowed to trust it.
 *
 * <p>This is the pair: one implementation your test must reject, one it must accept.
 * It challenges the test, not the code. A green suite that asserts nothing passes both
 * halves of nothing — and every decision the outer loop makes afterwards reads the
 * output of a check like this one.
 *
 * <p>It is a cheap control pair, not mutation testing, and it is no evidence that the
 * business rules are complete.
 */
class BorrowPolicyControlTest {

    @Test
    @DisplayName("Your borrowing check rejects an implementation that misses the rule")
    void rejectsTheKnownDefect() throws Exception {
        var workspace = Workspace.create();
        workspace.place(Candidates.missesSecondBorrowRule());

        var run = workspace.maven("-B", "-Dtest=BorrowPolicyTest", "test");

        assertNotEquals(0, run.exitCode(), """
                BAD CONTROL did not fail.

                BorrowPolicyTest passed against an implementation where a second member
                silently takes over a book that is already on loan. A check that cannot
                detect that defect is not yet a sensor, and the outer loop cannot rely
                on it. Finish the assertions in BorrowPolicyTest first."""
                + "\n\nMaven output:\n" + run.output());
    }

    @Test
    @DisplayName("Your borrowing check accepts a valid implementation")
    void acceptsAValidImplementation() throws Exception {
        var workspace = Workspace.create();
        workspace.place(Candidates.valid());

        var run = workspace.maven("-B", "-Dtest=BorrowPolicyTest", "test");

        assertEquals(0, run.exitCode(), """
                GOOD CONTROL did not pass.

                BorrowPolicyTest rejected a correct implementation. A check that fires on
                valid code will send the agent chasing a defect that is not there, and it
                will burn your repair budget doing it."""
                + "\n\nMaven output:\n" + run.output());
    }
}
