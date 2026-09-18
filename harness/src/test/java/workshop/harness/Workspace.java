package workshop.harness;

import workshop.harness.internal.Candidates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * A disposable copy of the Bookshelf to run a scenario against.
 *
 * <p>Nothing here ever touches your working tree. Every scenario gets its own temporary
 * directory, and the copy includes YOUR BorrowPolicyTest — the loop is only as good as
 * the business check you gave it.
 */
final class Workspace {

    record MavenRun(int exitCode, String output) { }

    private final Path root;

    private Workspace(Path root) {
        this.root = root;
    }

    /** Copy the real Bookshelf into a fresh temporary root. */
    static Workspace create() throws IOException {
        var source = Path.of(System.getProperty("workshop.bookshelf.dir", "../bookshelf"))
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IOException("Cannot find the Bookshelf at " + source);
        }
        var root = Files.createTempDirectory("outer-harness-");
        copyTree(source, root.resolve("bookshelf"));
        return new Workspace(root);
    }

    Path root() {
        return root;
    }

    Path bookshelf() {
        return root.resolve("bookshelf");
    }

    String approvedPolicy() throws IOException {
        return Files.readString(bookshelf().resolve("approved-policy.md"));
    }

    /** Put a known candidate in place, as if an agent had just written it. */
    void place(Candidates candidate) throws IOException {
        write("src/main/java/workshop/bookshelf/service/BorrowService.java",
                candidate.borrowService());
        write("src/main/java/workshop/bookshelf/domain/Book.java", candidate.book());
    }

    /** Make the build unconfigurable, so checks ERROR instead of returning a verdict. */
    void breakTheBuildTooling() throws IOException {
        Files.writeString(bookshelf().resolve("pom.xml"), "not a pom");
    }

    /** Run Maven inside the copy and return its exit code. */
    MavenRun maven(String... args) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<String>();
        command.add(System.getProperty("workshop.maven.cmd", "./mvnw"));
        command.addAll(java.util.List.of(args));
        var log = Files.createTempFile("workspace-maven-", ".log");
        var process = new ProcessBuilder(command).directory(bookshelf().toFile())
                .redirectInput(new java.io.File("/dev/null"))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(180, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Maven timed out; output in " + log);
        }
        var output = Files.readString(log);
        Files.deleteIfExists(log);
        return new MavenRun(process.exitValue(), output);
    }

    private void write(String relativePath, String source) throws IOException {
        var target = bookshelf().resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, source);
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (var paths = Files.walk(from)) {
            for (var source : paths.toList()) {
                var relative = from.relativize(source);
                if (isLocalBuildState(relative)) continue;
                var target = to.resolve(relative.toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static boolean isLocalBuildState(Path relative) {
        if (relative.getNameCount() == 0) return false;
        var first = relative.getName(0).toString();
        return first.equals("target") || first.equals(".idea");
    }
}
