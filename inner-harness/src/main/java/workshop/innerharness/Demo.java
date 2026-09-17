package workshop.innerharness;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Optional live run after the deterministic bonus exercises pass. */
public final class Demo {
    private Demo() {}

    public static void main(String[] args) throws IOException {
        var workspace = Path.of("inner-harness/target/live-workspace");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("Example.java"), """
                class Example {
                    public static String label(String value) {
                        return "[" + value + "]";
                    }
                }
                """);

        var skills = Path.of("inner-harness/src/main/resources/skills");
        var tools = InnerHarness.defaultTools(workspace, skills, Demo::askPermission);
        var harness = new InnerHarness(AnthropicSession.fromEnvironment(), tools, 8);
        var task = args.length == 0
                ? "Load the concise-java skill, read Example.java, and write Review.md with a short review."
                : String.join(" ", args);

        var result = harness.run(task);
        for (var event : result.trace()) System.out.println(event);
        if (!result.answer().isBlank()) System.out.println("answer=" + result.answer());
        System.out.println("stop=" + result.stopReason());
        System.out.println("tokens=input:" + result.inputTokens() + " output:" + result.outputTokens());
    }

    private static boolean askPermission(String toolName, String detail) {
        System.out.print("Allow " + toolName + " for " + detail + "? [y/N] ");
        try {
            var answer = new BufferedReader(new InputStreamReader(System.in)).readLine();
            return answer != null && answer.trim().toLowerCase().startsWith("y");
        } catch (IOException exception) {
            return false;
        }
    }
}
