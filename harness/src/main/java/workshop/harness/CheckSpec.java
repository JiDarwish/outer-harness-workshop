package workshop.harness;

import java.util.List;

/**
 * One named check: what it is for, and how to run it.
 *
 * <p>Purpose and command stay separate on purpose. "the approved borrowing policy" is
 * the property a human cares about; {@code -Dtest=BorrowPolicyTest} is merely how it
 * happens to be executed today. A finding reports the first and can reproduce the second.
 */
record CheckSpec(String name, String property, List<String> command) {
    /** The exact command a reader can paste to see this result for themselves. */
    String rerun() {
        return "cd bookshelf && " + String.join(" ", command);
    }
}
