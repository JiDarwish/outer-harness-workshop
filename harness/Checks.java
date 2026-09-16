import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** One check on one agent attempt. The outer loop groups findings by attempt. */
record Finding(String name, String property, State state, String detail, String rerun,
               long elapsedMs, Path logPath) {
    enum State { UNCHECKED, PASS, FAIL, ERROR, SKIPPED }

    static Finding notWired() {
        return new Finding("UNWIRED_CHECK", "", State.UNCHECKED, "No check wired", "", 0, null);
    }

    static Finding skipped(CheckSpec spec, String reason) {
        return new Finding(spec.name(), spec.property(), State.SKIPPED, reason,
                spec.rerun(), 0, null);
    }

    boolean unchecked() { return state == State.UNCHECKED; }
    boolean failed() { return state == State.FAIL; }
    boolean error() { return state == State.ERROR; }
    boolean passed() { return state == State.PASS; }
}

/** Purpose and executable command stay separate: today's JUnit test is one business sensor. */
enum CheckKind { MAVEN, LINT }

record CheckSpec(String name, String property, List<String> command, CheckKind kind) {
    CheckSpec(String name, String property, List<String> command) {
        this(name, property, command, CheckKind.MAVEN);
    }

    String rerun() { return "cd bookshelf && " + String.join(" ", command); }
}

/** Supplied Maven process plumbing. Participants choose which named checks to run. */
final class Checks {
    static final CheckSpec COMPILE = new CheckSpec("COMPILE",
            "production Java sources compile",
            List.of("./mvnw", "-q", "-B", "compile"));
    static final CheckSpec STATIC_HYGIENE = new CheckSpec("STATIC_HYGIENE",
            "production library code does not write directly to the console",
            List.of("java", "../harness/StaticHygiene.java", "src/main/java"), CheckKind.LINT);
    static final CheckSpec BUSINESS_BEHAVIOR = new CheckSpec("BUSINESS_BEHAVIOR",
            "the approved borrowing policy",
            List.of("./mvnw", "-q", "-B", "-Dtest=BorrowPolicyTest", "test"));
    static final CheckSpec ARCHITECTURE_BOUNDARY = new CheckSpec("ARCHITECTURE_BOUNDARY",
            "domain classes do not depend on service or storage",
            List.of("./mvnw", "-q", "-B", "-Dtest=ArchitectureTest", "test"));
    static final CheckSpec REGRESSION_SUITE = new CheckSpec("REGRESSION_SUITE",
            "the complete Bookshelf test suite",
            List.of("./mvnw", "-q", "-B", "test"));

    private Checks() { }

    static Finding run(Path root, CheckSpec spec) {
        var bookshelf = root.resolve("bookshelf");
        var started = System.nanoTime();
        Path log = null;
        try {
            var reports = bookshelf.resolve("target/harness-reports");
            Files.createDirectories(reports);
            log = Files.createTempFile(reports, spec.name().toLowerCase() + "-", ".log");
            var process = new ProcessBuilder(spec.command()).directory(bookshelf.toFile())
                    .redirectInput(new java.io.File("/dev/null"))
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return result(spec, Finding.State.ERROR, "Check timed out after 120 seconds",
                        started, log);
            }
            var output = Files.readString(log);
            if (process.exitValue() == 0) {
                return result(spec, Finding.State.PASS, "", started, log);
            }
            if (applicationFailure(output, spec)) {
                return result(spec, Finding.State.FAIL, diagnostic(output, spec), started, log);
            }
            return result(spec, Finding.State.ERROR,
                    "Maven exited " + process.exitValue() + " without a recognizable code or test failure",
                    started, log);
        } catch (IOException e) {
            return result(spec, Finding.State.ERROR, "Could not run/read check: " + e.getMessage(),
                    started, log);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result(spec, Finding.State.ERROR, "Check command interrupted", started, log);
        }
    }

    private static boolean applicationFailure(String output, CheckSpec spec) {
        if (spec.kind() == CheckKind.LINT) return output.contains("[LINT]");
        return output.contains("There are test failures")
                || output.contains("COMPILATION ERROR")
                || output.lines().anyMatch(line -> line.contains("[ERROR] Tests run:")
                        && (line.matches(".*Failures: [1-9][0-9]*.*")
                            || line.matches(".*Errors: [1-9][0-9]*.*")));
    }

    private static String diagnostic(String output, CheckSpec spec) {
        if (spec.kind() == CheckKind.LINT) {
            return String.join("\n", output.lines().filter(line -> line.startsWith("[LINT]"))
                    .limit(6).map(Checks::shortLine).toList());
        }
        var useful = output.lines()
                .filter(line -> line.contains("expected:") || line.contains("Architecture Violation")
                        || line.matches("^(Method|Constructor|Field|Class) <.*")
                        || line.contains(".java:[") || line.contains("[ERROR] Failures:")
                        || line.contains("[ERROR] Errors:")
                        || line.contains("[ERROR] Tests run:"))
                .distinct().limit(6).map(Checks::shortLine).toList();
        if (!useful.isEmpty()) return String.join("\n", useful);
        var errors = output.lines().filter(line -> line.contains("[ERROR]"))
                .filter(line -> !line.contains("Failed to execute goal")
                        && !line.contains("See ") && !line.contains("[Help"))
                .distinct().limit(4).map(Checks::shortLine).toList();
        return errors.isEmpty() ? "Failed " + spec.property() + "; rerun for full output"
                : String.join("\n", errors);
    }

    private static String shortLine(String line) {
        return line.length() <= 220 ? line : line.substring(0, 217) + "...";
    }

    private static Finding result(CheckSpec spec, Finding.State state, String detail,
                                  long started, Path log) {
        return new Finding(spec.name(), spec.property(), state, detail, spec.rerun(),
                (System.nanoTime() - started) / 1_000_000, log);
    }
}
