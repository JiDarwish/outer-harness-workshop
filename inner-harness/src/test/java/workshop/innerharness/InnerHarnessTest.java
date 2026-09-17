package workshop.innerharness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InnerHarnessTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void stopsWhenTheModelReturnsACompleteAnswer() {
        var session = new ScriptedSession(InnerHarness.ModelTurn.text("Finished"));
        var harness = new InnerHarness(session, tools(), 4);

        var result = harness.run("Do the task");

        assertEquals(InnerHarness.StopReason.COMPLETED, result.stopReason());
        assertEquals(1, result.turns());
        assertEquals("Finished", result.answer());
        assertEquals(0, session.continuations.size());
    }

    @Test
    void executesAToolAndReturnsItsResultToTheConversation() throws IOException {
        Files.writeString(temporaryDirectory.resolve("Example.java"), "class Example {}");
        var session = new ScriptedSession(
                InnerHarness.ModelTurn.tools(call("read-1", "read_file", "path", "Example.java")),
                InnerHarness.ModelTurn.text("I read it"));
        var harness = new InnerHarness(session, tools(), 4);

        var result = harness.run("Read Example.java");

        assertEquals(InnerHarness.StopReason.COMPLETED, result.stopReason());
        assertEquals(2, result.turns());
        var toolResult = session.continuations.getFirst().getFirst();
        assertTrue(toolResult.succeeded());
        assertTrue(toolResult.content().contains("class Example"));
        assertTrue(result.trace().stream().anyMatch(line -> line.contains("tool=read_file result=pass")));
    }

    @Test
    void stopsWithoutMakingAnotherModelCallWhenTheTurnBudgetIsSpent() {
        var session = new ScriptedSession(
                InnerHarness.ModelTurn.tools(call("read-1", "read_file", "path", "missing.txt")),
                InnerHarness.ModelTurn.tools(call("read-2", "read_file", "path", "missing.txt")),
                InnerHarness.ModelTurn.text("This third turn must never be requested"));
        var harness = new InnerHarness(session, tools(), 2);

        var result = harness.run("Keep reading");

        assertEquals(InnerHarness.StopReason.TURN_LIMIT_REACHED, result.stopReason());
        assertEquals(2, result.turns());
        assertEquals(1, session.continuations.size());
    }

    @Test
    void loadsSkillContentOnlyWhenTheModelRequestsIt() throws IOException {
        var skill = temporaryDirectory.resolve("skills/concise-java/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "Prefer names that state intent.");
        var session = new ScriptedSession(
                InnerHarness.ModelTurn.tools(call("skill-1", "load_skill", "name", "concise-java")),
                InnerHarness.ModelTurn.text("Skill loaded"));
        var harness = new InnerHarness(session,
                InnerHarness.defaultTools(temporaryDirectory, temporaryDirectory.resolve("skills"), (tool, detail) -> false),
                4);

        var result = harness.run("Use the concise Java conventions");

        assertEquals(InnerHarness.StopReason.COMPLETED, result.stopReason());
        var toolResult = session.continuations.getFirst().getFirst();
        assertTrue(toolResult.succeeded());
        assertTrue(toolResult.content().contains("names that state intent"));
    }

    @Test
    void aDeniedWriteBecomesVisibleEvidenceForTheModel() {
        var session = new ScriptedSession(
                InnerHarness.ModelTurn.tools(new InnerHarness.ToolCall("write-1", "write_file",
                        java.util.Map.of("path", "Result.md", "content", "Hello"))),
                InnerHarness.ModelTurn.text("The write was denied"));
        var harness = new InnerHarness(session, tools(), 4);

        var result = harness.run("Write Result.md");

        assertEquals(InnerHarness.StopReason.COMPLETED, result.stopReason());
        var toolResult = session.continuations.getFirst().getFirst();
        assertFalse(toolResult.succeeded());
        assertTrue(toolResult.content().contains("Permission denied"));
        assertFalse(Files.exists(temporaryDirectory.resolve("Result.md")));
    }

    private InnerHarness.ToolRegistry tools() {
        return InnerHarness.defaultTools(
                temporaryDirectory,
                temporaryDirectory.resolve("skills"),
                (tool, detail) -> false);
    }

    private static InnerHarness.ToolCall call(
            String id, String name, String argumentName, String argumentValue) {
        return new InnerHarness.ToolCall(id, name, java.util.Map.of(argumentName, argumentValue));
    }

    private static final class ScriptedSession implements InnerHarness.ModelSession {
        private final ArrayDeque<InnerHarness.ModelTurn> turns;
        private final List<List<InnerHarness.ToolResult>> continuations = new ArrayList<>();

        private ScriptedSession(InnerHarness.ModelTurn... turns) {
            this.turns = new ArrayDeque<>(List.of(turns));
        }

        @Override
        public InnerHarness.ModelTurn start(String task) {
            return next();
        }

        @Override
        public InnerHarness.ModelTurn continueWith(List<InnerHarness.ToolResult> results) {
            continuations.add(List.copyOf(results));
            return next();
        }

        private InnerHarness.ModelTurn next() {
            if (turns.isEmpty()) throw new AssertionError("Harness requested an unexpected model turn");
            return turns.removeFirst();
        }
    }
}
