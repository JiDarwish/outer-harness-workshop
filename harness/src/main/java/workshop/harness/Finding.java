package workshop.harness;

import java.nio.file.Path;

/**
 * One check, on one agent attempt.
 *
 * <p>Five states, not two. Colour is a convenience; the label is the contract, and the
 * label is what your code branches on.
 *
 * <ul>
 *   <li>{@code PASS} — the check ran and the property holds on this source
 *   <li>{@code FAIL} — the check ran and the property does not hold
 *   <li>{@code ERROR} — the check could not produce a verdict at all
 *   <li>{@code SKIPPED} — the stage did not run, and says so. Evidence of absence
 *   <li>{@code UNCHECKED} — nothing was wired here yet, so it must not be able to accept
 * </ul>
 */
public record Finding(String name, String property, State state, String detail, String rerun,
                      long elapsedMs, Path logPath) {

    public enum State { UNCHECKED, PASS, FAIL, ERROR, SKIPPED }

    static Finding notWired() {
        return new Finding("UNWIRED_CHECK", "", State.UNCHECKED, "No check wired", "", 0, null);
    }

    static Finding skipped(CheckSpec spec, String reason) {
        return new Finding(spec.name(), spec.property(), State.SKIPPED, reason,
                spec.rerun(), 0, null);
    }

    public boolean unchecked() { return state == State.UNCHECKED; }
    public boolean failed() { return state == State.FAIL; }
    public boolean error() { return state == State.ERROR; }
    public boolean passed() { return state == State.PASS; }
}
