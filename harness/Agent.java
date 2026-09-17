import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The existing coding agent, viewed through the one operation the outer loop needs. */
interface Agent {
    AgentResult build(String prompt);
}

record AgentResult(String summary, boolean failed, long elapsedMs,
                   Long inputTokens, Long outputTokens, Long cachedInputTokens) {
    static String show(Long value) { return value == null ? "unavailable" : value.toString(); }
}

final class Agents {
    private Agents() { }

    static Agent create(String mode, Path productionSources) {
        return switch (mode) {
            case "check-only" -> prompt -> new AgentResult(
                    "no agent; checked existing source", false, 0, null, null, null);
            // These compact scenarios are used only by verify-harness.sh.
            case "valid-first", "demo", "compile-gate", "repair-regression",
                 "agent-failure" -> new ScriptedAgent(mode, productionSources);
            case "claude", "codex" -> new CliAgent(mode, productionSources);
            default -> throw new IllegalArgumentException("Unknown agent mode: " + mode);
        };
    }
}

/** Prepared controls change production sources only; prompts and token counts stay visible. */
final class ScriptedAgent implements Agent {
    private final String mode;
    private final Path productionSources;
    private final Path fixtures;
    private int calls;

    ScriptedAgent(String mode, Path productionSources) {
        this.mode = mode;
        this.productionSources = productionSources;
        this.fixtures = productionSources.getParent().getParent().getParent().getParent()
                .resolve("fixtures");
    }

    @Override public AgentResult build(String prompt) {
        var started = System.nanoTime();
        calls++;
        IO.println("\n[SCRIPTED " + mode + " attempt=" + calls + " received]\n" + prompt);
        if (mode.equals("agent-failure")) {
            return new AgentResult("scripted agent failure", true, elapsed(started),
                    null, null, null);
        }
        try {
            switch (mode) {
                case "valid-first" -> {
                    service("good");
                    book("architecture-good");
                }
                case "demo" -> {
                    IO.println(calls == 1
                            ? "[PREPARED EDIT] BorrowService gets console output and the known "
                                    + "borrowing defect; Book gets a domain-to-storage dependency."
                            : "[PREPARED EDIT] BorrowService and Book are replaced with the valid controls.");
                    service(calls > 1 ? "good" : "all-bad");
                    book(calls > 1 ? "architecture-good" : "architecture-bad");
                }
                case "repair-regression" -> {
                    service(calls > 1 ? "good" : "bad");
                    book(calls > 1 ? "architecture-bad" : "architecture-good");
                }
                case "compile-gate" -> {
                    service(calls > 1 ? "good" : "compile-bad");
                    book("architecture-good");
                }
                default -> throw new IllegalArgumentException("Unsupported scripted mode: " + mode);
            }
            return new AgentResult("scripted " + mode + " attempt " + calls, false,
                    elapsed(started), null, null, null);
        } catch (IOException e) {
            return new AgentResult("scripted fixture failed: " + e.getMessage(), true,
                    elapsed(started), null, null, null);
        }
    }

    private void service(String fixture) throws IOException {
        Files.copy(fixtures.resolve(fixture + "/BorrowService.java"),
                productionSources.resolve("workshop/bookshelf/service/BorrowService.java"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void book(String fixture) throws IOException {
        Files.copy(fixtures.resolve(fixture + "/Book.java"),
                productionSources.resolve("workshop/bookshelf/domain/Book.java"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}

/**
 * Supplied adapter. Each call gives the CLI an isolated copy of production Java sources.
 * Only Java files from that copy come back; attendee-owned checks are never in its input.
 */
final class CliAgent implements Agent {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String mode;
    private final Path productionSources;

    CliAgent(String mode, Path productionSources) {
        this.mode = mode;
        this.productionSources = productionSources;
    }

    @Override
    public AgentResult build(String prompt) {
        var started = System.nanoTime();
        try {
            IO.println("Starting " + mode
                    + " in an isolated production-source copy (timeout: 180 seconds)...");
            var isolated = Files.createTempDirectory("bookshelf-agent-");
            var isolatedSources = isolated.resolve("src");
            copyJava(productionSources, isolatedSources);
            Files.writeString(isolated.resolve("README.md"), """
                    This is the Bookshelf production source tree. Edit Java files in src/ only.
                    There is no test suite in this workspace. The outer harness runs its own
                    checks after you finish and will send back any failure for repair.
                    """);

            var command = command(isolated, prompt);
            var log = Files.createTempFile("bookshelf-agent-output-", ".log");
            var process = new ProcessBuilder(command).directory(isolated.toFile())
                    .redirectInput(new java.io.File("/dev/null"))
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new AgentResult("agent timed out after 180 seconds", true,
                        elapsed(started), null, null, null);
            }
            var output = Files.readString(log);
            if (process.exitValue() != 0) {
                return new AgentResult("agent exited " + process.exitValue() + ": " + failureSummary(output),
                        true, elapsed(started), null, null, null);
            }
            var result = parse(output, elapsed(started));
            if (!result.failed()) copyJava(isolatedSources, productionSources);
            return result;
        } catch (IOException e) {
            return new AgentResult("agent could not start: " + e.getMessage(), true,
                    elapsed(started), null, null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AgentResult("agent interrupted", true, elapsed(started), null, null, null);
        }
    }

    private List<String> command(Path isolated, String prompt) {
        if (mode.equals("claude")) {
            return List.of("claude", "-p", prompt, "--output-format", "json",
                    "--model", "haiku",
                    "--no-session-persistence",
                    "--restricted", "--permission-mode", "acceptEdits",
                    "--tools", "Read,Write,Edit,Glob,Grep");
        }
        var command = new ArrayList<>(List.of("codex", "exec", "--json",
                "--model", "gpt-5.5", "--sandbox", "workspace-write",
                "--skip-git-repo-check", "--ephemeral", "--ignore-rules",
                "-C", isolated.toString()));
        command.add(prompt);
        return command;
    }

    private AgentResult parse(String output, long millis) {
        try {
            if (mode.equals("claude")) {
                var result = JSON.readTree(output);
                var usage = result.path("usage");
                return new AgentResult(firstLine(result.path("result").asText("done")),
                        result.path("is_error").asBoolean(false), millis,
                        number(usage, "input_tokens"), number(usage, "output_tokens"),
                        number(usage, "cache_read_input_tokens"));
            }
            String summary = "done";
            boolean failed = false;
            Long input = null, outputTokens = null, cached = null;
            for (var line : output.lines().toList()) {
                if (!line.startsWith("{")) continue;
                var event = JSON.readTree(line);
                if (event.path("type").asText().equals("item.completed")
                        && event.path("item").path("type").asText().equals("agent_message")) {
                    summary = firstLine(event.path("item").path("text").asText("done"));
                }
                if (event.path("type").asText().equals("turn.completed")) {
                    var usage = event.path("usage");
                    input = number(usage, "input_tokens");
                    outputTokens = number(usage, "output_tokens");
                    cached = number(usage, "cached_input_tokens");
                }
                if (event.path("type").asText().equals("turn.failed")
                        || event.path("type").asText().equals("error")) failed = true;
            }
            return new AgentResult(summary, failed, millis, input, outputTokens, cached);
        } catch (IOException e) {
            return new AgentResult("agent completed; usage unavailable", false, millis,
                    null, null, null);
        }
    }

    private String failureSummary(String output) {
        try {
            if (mode.equals("claude")) {
                return firstLine(JSON.readTree(output).path("result").asText(output));
            }
            for (var line : output.lines().toList()) {
                if (!line.startsWith("{")) continue;
                var event = JSON.readTree(line);
                if (event.path("type").asText().equals("error")) {
                    return firstLine(event.path("message").asText(output));
                }
            }
        } catch (IOException ignored) {
            // Plain CLI errors are also useful enough to display.
        }
        return firstLine(output);
    }

    private static Long number(JsonNode node, String name) {
        return node.hasNonNull(name) ? node.get(name).asLong() : null;
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse("done").strip();
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static void copyJava(Path from, Path to) throws IOException {
        try (var files = Files.walk(from)) {
            for (var source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var target = to.resolve(from.relativize(source));
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
