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
            case "noop" -> prompt -> {
                IO.println("\n[NOOP agent received]\n" + prompt);
                return new AgentResult("no-op test double", false, 0, null, null, null);
            };
            case "claude", "codex" -> new CliAgent(mode, productionSources);
            default -> throw new IllegalArgumentException("Use --agent=noop, --agent=claude or --agent=codex");
        };
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
                    "--restricted", "--permission-mode", "acceptEdits",
                    "--tools", "Read,Write,Edit,Glob,Grep");
        }
        var command = new ArrayList<>(List.of("codex", "exec", "--json", "--sandbox",
                "workspace-write", "--skip-git-repo-check", "--ephemeral", "-C", isolated.toString()));
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
