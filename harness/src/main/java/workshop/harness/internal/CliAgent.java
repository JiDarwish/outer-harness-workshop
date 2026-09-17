package workshop.harness.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import workshop.harness.Agent;
import workshop.harness.AgentResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Supplied adapter for a real coding agent.
 *
 * <p>Each call hands the CLI an isolated copy of production Java sources, and only Java
 * files from that copy come back. Your checks are never in its input — that is what
 * keeps them independent evidence rather than something the agent can write towards.
 *
 * <p>Participants never need to open this file.
 */
public final class CliAgent implements Agent {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final String mode;
    private final Path productionSources;

    CliAgent(String mode, Path productionSources) {
        this.mode = mode;
        this.productionSources = productionSources;
    }

    @Override
    public AgentResult build(String prompt) {
        return build(prompt, ignored -> { });
    }

    @Override
    public AgentResult build(String prompt, Consumer<String> activity) {
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
                    .redirectErrorStream(true).start();
            var processOutput = collect(process, log, isolated, activity);
            if (processOutput.timedOut()) {
                return new AgentResult("agent timed out after 180 seconds", true,
                        elapsed(started), null, null, null);
            }
            if (processOutput.readFailure() != null) {
                return new AgentResult("agent output could not be read: "
                        + processOutput.readFailure().getMessage(), true, elapsed(started),
                        null, null, null);
            }
            if (processOutput.exitCode() != 0) {
                return new AgentResult("agent exited " + processOutput.exitCode() + ": "
                        + failureSummary(processOutput.text()), true, elapsed(started),
                        null, null, null);
            }
            var result = parse(processOutput.text(), elapsed(started));
            if (!result.failed()) {
                var changedFiles = changedJavaFiles(productionSources, isolatedSources);
                copyJava(isolatedSources, productionSources);
                result = result.withChangedFiles(changedFiles);
            }
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
            return List.of("claude", "-p", prompt, "--output-format", "stream-json",
                    "--verbose",
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

    private ProcessOutput collect(Process process, Path log, Path isolated,
                                  Consumer<String> activity) throws InterruptedException {
        var output = new StringBuilder();
        var readFailure = new AtomicReference<IOException>();
        Set<String> seenToolCalls = new HashSet<>();
        var reader = Thread.ofVirtual().start(() -> {
            try (var lines = process.inputReader(StandardCharsets.UTF_8);
                 var writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8)) {
                String line;
                while ((line = lines.readLine()) != null) {
                    output.append(line).append('\n');
                    writer.write(line);
                    writer.newLine();
                    publishActivity(line, isolated, activity, seenToolCalls);
                }
            } catch (IOException e) {
                readFailure.set(e);
            }
        });

        var finished = process.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        reader.join(5_000);
        if (reader.isAlive()) reader.interrupt();
        return new ProcessOutput(finished ? process.exitValue() : -1, !finished,
                output.toString(), readFailure.get());
    }

    private void publishActivity(String line, Path isolated, Consumer<String> activity,
                                 Set<String> seenToolCalls) {
        for (var message : visibleActivity(mode, line, isolated, seenToolCalls)) {
            activity.accept(message);
        }
    }

    static List<String> visibleActivity(String mode, String line, Path isolated,
                                        Set<String> seenToolCalls) {
        var messages = new ArrayList<String>();
        if (!line.startsWith("{")) return List.of();
        try {
            var event = JSON.readTree(line);
            if (mode.equals("claude")) {
                if (!event.path("type").asText().equals("assistant")) return List.of();
                for (var block : event.path("message").path("content")) {
                    if (!block.path("type").asText().equals("tool_use")) continue;
                    var id = block.path("id").asText(block.toString());
                    if (!seenToolCalls.add(id)) continue;
                    var description = describeClaudeTool(block.path("name").asText(),
                            block.path("input"), isolated);
                    if (description != null) messages.add(description);
                }
                return List.copyOf(messages);
            }

            var item = event.path("item");
            if (!event.path("type").asText().equals("item.completed")
                    || !item.path("type").asText().equals("file_change")) return List.of();
            for (var change : item.path("changes")) {
                messages.add("Edit   " + displayPath(change.path("path").asText(), isolated));
            }
        } catch (IOException ignored) {
            // Non-JSON provider diagnostics remain in the raw log and final error summary.
        }
        return List.copyOf(messages);
    }

    private static String describeClaudeTool(String name, JsonNode input, Path isolated) {
        return switch (name) {
            case "Read" -> "Read   " + displayPath(input.path("file_path").asText(), isolated);
            case "Edit" -> "Edit   " + displayPath(input.path("file_path").asText(), isolated);
            case "Write" -> "Write  " + displayPath(input.path("file_path").asText(), isolated);
            case "Grep" -> "Grep   \"" + shortText(input.path("pattern").asText()) + "\"";
            case "Glob" -> "Glob   " + shortText(input.path("pattern").asText());
            default -> null;
        };
    }

    private AgentResult parse(String output, long millis) {
        try {
            if (mode.equals("claude")) {
                JsonNode result = null;
                for (var line : output.lines().toList()) {
                    if (!line.startsWith("{")) continue;
                    var event = JSON.readTree(line);
                    if (event.path("type").asText().equals("result")) result = event;
                }
                if (result == null) {
                    return new AgentResult("agent completed; result event unavailable", false,
                            millis, null, null, null);
                }
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
                String summary = null;
                for (var line : output.lines().toList()) {
                    if (!line.startsWith("{")) continue;
                    var event = JSON.readTree(line);
                    if (event.path("type").asText().equals("result")) {
                        summary = event.path("result").asText();
                    }
                }
                return firstLine(summary == null || summary.isBlank() ? output : summary);
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

    private static String displayPath(String raw, Path isolated) {
        if (raw == null || raw.isBlank()) return "unknown file";
        try {
            var path = Path.of(raw).normalize();
            if (path.isAbsolute() && path.startsWith(isolated.normalize())) {
                path = isolated.normalize().relativize(path);
            }
            return path.getFileName() == null ? path.toString() : path.getFileName().toString();
        } catch (RuntimeException ignored) {
            return raw;
        }
    }

    private static String shortText(String text) {
        if (text == null || text.isBlank()) return "?";
        return text.length() <= 60 ? text : text.substring(0, 57) + "...";
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static List<String> changedJavaFiles(Path original, Path candidate)
            throws IOException {
        var changed = new TreeSet<String>();
        try (var files = Files.walk(candidate)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var relative = candidate.relativize(file);
                var before = original.resolve(relative);
                if (!Files.exists(before) || Files.mismatch(before, file) != -1) {
                    changed.add(relative.getFileName().toString());
                }
            }
        }
        return List.copyOf(changed);
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

    private record ProcessOutput(int exitCode, boolean timedOut, String text,
                                 IOException readFailure) { }
}
