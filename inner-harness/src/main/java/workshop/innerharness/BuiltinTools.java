package workshop.innerharness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Supplied tool implementations. The bonus exercise wires them into the loop. */
public final class BuiltinTools {
    private BuiltinTools() {}

    @FunctionalInterface
    public interface ApprovalPolicy {
        boolean allow(String toolName, String detail);
    }

    public static final class ReadFile implements InnerHarness.Tool {
        private final Path workspace;

        public ReadFile(Path workspace) {
            this.workspace = workspace.toAbsolutePath().normalize();
        }

        @Override
        public String name() {
            return "read_file";
        }

        @Override
        public InnerHarness.Execution execute(Map<String, String> arguments) {
            var requested = required(arguments, "path");
            try {
                return InnerHarness.Execution.success(Files.readString(inside(workspace, requested)));
            } catch (IOException exception) {
                return InnerHarness.Execution.failure("Could not read " + requested + ": " + exception.getMessage());
            } catch (IllegalArgumentException exception) {
                return InnerHarness.Execution.failure(exception.getMessage());
            }
        }
    }

    public static final class WriteFile implements InnerHarness.Tool {
        private final Path workspace;
        private final ApprovalPolicy approvalPolicy;

        public WriteFile(Path workspace, ApprovalPolicy approvalPolicy) {
            this.workspace = workspace.toAbsolutePath().normalize();
            this.approvalPolicy = approvalPolicy;
        }

        @Override
        public String name() {
            return "write_file";
        }

        @Override
        public InnerHarness.Execution execute(Map<String, String> arguments) {
            var requested = required(arguments, "path");
            var content = required(arguments, "content");
            if (!approvalPolicy.allow(name(), requested)) {
                return InnerHarness.Execution.failure("Permission denied for " + requested);
            }
            try {
                var destination = inside(workspace, requested);
                if (destination.getParent() != null) Files.createDirectories(destination.getParent());
                Files.writeString(destination, content);
                return InnerHarness.Execution.success("Wrote " + content.length() + " characters to " + requested);
            } catch (IOException exception) {
                return InnerHarness.Execution.failure("Could not write " + requested + ": " + exception.getMessage());
            } catch (IllegalArgumentException exception) {
                return InnerHarness.Execution.failure(exception.getMessage());
            }
        }
    }

    public static final class LoadSkill implements InnerHarness.Tool {
        private final Path skillRoot;

        public LoadSkill(Path skillRoot) {
            this.skillRoot = skillRoot.toAbsolutePath().normalize();
        }

        @Override
        public String name() {
            return "load_skill";
        }

        @Override
        public InnerHarness.Execution execute(Map<String, String> arguments) {
            var name = required(arguments, "name");
            if (!name.matches("[a-z0-9-]+")) {
                return InnerHarness.Execution.failure("Invalid skill name: " + name);
            }
            try {
                var file = inside(skillRoot, name + "/SKILL.md");
                return InnerHarness.Execution.success(Files.readString(file));
            } catch (IOException exception) {
                return InnerHarness.Execution.failure("Skill not found: " + name);
            } catch (IllegalArgumentException exception) {
                return InnerHarness.Execution.failure(exception.getMessage());
            }
        }
    }

    private static String required(Map<String, String> arguments, String name) {
        var value = arguments.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing argument: " + name);
        return value;
    }

    private static Path inside(Path root, String requested) {
        var resolved = root.resolve(requested).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Path leaves the allowed directory");
        return resolved;
    }
}
