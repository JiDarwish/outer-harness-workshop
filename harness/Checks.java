import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** A result the outer loop can trust as evidence about one declared check. */
record Finding(State state, String detail, String rerun, long elapsedMs) {
    enum State { UNCHECKED, PASS, FAIL, ERROR }

    static Finding notWired() { return new Finding(State.UNCHECKED, "No check wired", "", 0); }
    boolean unchecked() { return state == State.UNCHECKED; }
    boolean failed() { return state == State.FAIL; }
    boolean error() { return state == State.ERROR; }
}

/** Supplied process plumbing. Attendees only add the call to this function. */
final class Checks {
    private Checks() { }

    static Finding run(Path root) {
        var bookshelf = root.resolve("bookshelf");
        var command = "cd bookshelf && ./mvnw -q -B test";
        var started = System.nanoTime();
        try {
            var log = Files.createTempFile("bookshelf-check-", ".log");
            var process = new ProcessBuilder("./mvnw", "-q", "-B", "test")
                    .directory(bookshelf.toFile()).redirectErrorStream(true)
                    .redirectOutput(log.toFile()).start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Finding(Finding.State.ERROR, "Test command timed out after 120 seconds",
                        command, elapsed(started));
            }
            if (process.exitValue() == 0) {
                return new Finding(Finding.State.PASS, "", command, elapsed(started));
            }
            var lines = Arrays.asList(Files.readString(log).split("\n"));
            var useful = lines.stream()
                    .filter(line -> line.contains("expected:") || line.contains("Architecture Violation")
                            || line.matches(".*\\.java:\\[[0-9]+,[0-9]+].*"))
                    .distinct().limit(8).toList();
            if (useful.isEmpty()) {
                useful = lines.stream().filter(line -> line.contains("[ERROR]"))
                        .filter(line -> !line.contains("Failed to execute goal")
                                && !line.contains("See /Users/") && !line.contains("[Help"))
                        .limit(8).toList();
            }
            var detail = useful.isEmpty() ? "Test command failed; run " + command + " for full output"
                    : String.join("\n", useful);
            return new Finding(Finding.State.FAIL, detail, command, elapsed(started));
        } catch (IOException e) {
            return new Finding(Finding.State.ERROR, "Could not run tests: " + e.getMessage(),
                    command, elapsed(started));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Finding(Finding.State.ERROR, "Test command interrupted", command, elapsed(started));
        }
    }

    private static long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
