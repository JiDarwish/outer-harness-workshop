package workshop.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import workshop.harness.Finding.State;

/** Supplied process plumbing. Participants choose which named checks to run, and when. */
final class Checks {

    /**
     * How to invoke Maven. Defaults to the wrapper inside the Bookshelf copy; override
     * with -Dworkshop.maven.cmd=mvn where a system Maven is preferred (CI, offline).
     */
    private static final String MAVEN = System.getProperty("workshop.maven.cmd", "./mvnw");


    static final CheckSpec COMPILE = new CheckSpec("COMPILE",
            "production Java sources compile",
            List.of(MAVEN, "-B", "compile"));
    static final CheckSpec STATIC_HYGIENE = new CheckSpec("STATIC_HYGIENE",
            "production library code does not write directly to the console",
            List.of(), CheckKind.LINT);
    static final CheckSpec BUSINESS_BEHAVIOR = new CheckSpec("BUSINESS_BEHAVIOR",
            "the approved borrowing policy",
            List.of(MAVEN, "-B", "-Dtest=BorrowPolicyTest", "test"));
    static final CheckSpec ARCHITECTURE_BOUNDARY = new CheckSpec("ARCHITECTURE_BOUNDARY",
            "domain classes do not depend on service or storage",
            List.of(MAVEN, "-B", "-Dtest=ArchitectureTest", "test"));
    static final CheckSpec FULL_TEST_SUITE = new CheckSpec("FULL_TEST_SUITE",
            "all existing Bookshelf tests still pass",
            List.of(MAVEN, "-B", "test"));

    private Checks() { }

    static Finding run(Path root, CheckSpec spec) {
        var bookshelf = root.resolve("bookshelf");
        var started = System.nanoTime();
        Path log = null;
        try {
            var reports = bookshelf.resolve("target/harness-reports");
            Files.createDirectories(reports);
            log = Files.createTempFile(reports, spec.name().toLowerCase() + "-", ".log");
            return spec.kind() == CheckKind.LINT
                    ? lint(spec, bookshelf, started, log)
                    : maven(spec, bookshelf, started, log);
        } catch (IOException e) {
            return result(spec, State.ERROR, "Could not run/read check: " + e.getMessage(),
                    started, log);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result(spec, State.ERROR, "Check command interrupted", started, log);
        }
    }

    /** The static sensor is a library call, not a subprocess: same verdict, no process spawn. */
    private static Finding lint(CheckSpec spec, Path bookshelf, long started, Path log) {
        try {
            var inspection = StaticHygiene.inspect(bookshelf.resolve("src/main/java"));
            Files.writeString(log, inspection.output());
            return inspection.clean()
                    ? result(spec, State.PASS, "", started, log)
                    : result(spec, State.FAIL, diagnostic(inspection.output(), spec),
                            started, log);
        } catch (Exception e) {
            return result(spec, State.ERROR,
                    "Static sensor could not produce a verdict: " + e.getMessage(), started, log);
        }
    }

    private static Finding maven(CheckSpec spec, Path bookshelf, long started, Path log)
            throws IOException, InterruptedException {
        var process = new ProcessBuilder(spec.command()).directory(bookshelf.toFile())
                .redirectInput(new java.io.File("/dev/null"))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return result(spec, State.ERROR, "Check timed out after 120 seconds",
                    started, log);
        }
        var output = Files.readString(log);
        if (process.exitValue() == 0) {
            return result(spec, State.PASS, "", started, log);
        }
        if (applicationFailure(output, spec)) {
            return result(spec, State.FAIL, diagnostic(output, spec), started, log);
        }
        return result(spec, State.ERROR,
                "Maven exited " + process.exitValue()
                        + " without a recognizable code or test failure", started, log);
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
        var useful = output.lines().map(String::strip)
                .filter(line -> line.startsWith("[ERROR]   ")
                        && (line.contains("expected:") || line.contains("AssertionFailedError")))
                .map(Checks::withoutMavenPrefix)
                .distinct().limit(1).map(Checks::shortLine).toList();
        if (!useful.isEmpty()) return String.join("\n", useful);

        useful = output.lines().map(String::strip)
                .filter(line -> line.contains("Architecture Violation")
                        || line.matches("^(Method|Constructor|Field|Class) <.*")
                        || line.contains(".java:["))
                .map(Checks::withoutMavenPrefix)
                .distinct().limit(4).map(Checks::shortLine).toList();
        if (!useful.isEmpty()) return String.join("\n", useful);
        var errors = output.lines().filter(line -> line.contains("[ERROR]"))
                .filter(line -> !line.contains("Failed to execute goal")
                        && !line.contains("See ") && !line.contains("[Help"))
                .map(Checks::withoutMavenPrefix)
                .distinct().limit(2).map(Checks::shortLine).toList();
        return errors.isEmpty() ? "Failed " + spec.property() + "; rerun for full output"
                : String.join("\n", errors);
    }

    private static String withoutMavenPrefix(String line) {
        return line.replaceFirst("^\\[ERROR]\\s*", "")
                .replaceFirst("expected: <([^>]*)> but was: <([^>]*)>",
                        "expected $1 but was $2");
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
