package workshop.harness.internal;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliAgentActivityTest {

    @Test
    void translatesClaudeToolEventsWithoutRepeatingThem() {
        var seen = new HashSet<String>();
        var root = Path.of("/tmp/bookshelf-agent-example");
        var event = """
                {"type":"assistant","message":{"content":[
                  {"type":"tool_use","id":"tool-1","name":"Read",
                   "input":{"file_path":"/tmp/bookshelf-agent-example/src/BorrowService.java"}},
                  {"type":"tool_use","id":"tool-2","name":"Grep",
                   "input":{"pattern":"borrow"}}
                ]}}
                """.replace("\n", "");

        assertEquals(java.util.List.of("Read   BorrowService.java", "Grep   \"borrow\""),
                CliAgent.visibleActivity("claude", event, root, seen));
        assertEquals(java.util.List.of(),
                CliAgent.visibleActivity("claude", event, root, seen),
                "stream updates may repeat a tool block, but the stage view should not");
    }

    @Test
    void translatesCodexFileChangesThroughTheSameView() {
        var event = """
                {"type":"item.completed","item":{"type":"file_change","changes":[
                  {"path":"/tmp/bookshelf-agent-example/src/Book.java","kind":"update"}
                ]}}
                """.replace("\n", "");

        assertEquals(java.util.List.of("Edit   Book.java"),
                CliAgent.visibleActivity("codex", event,
                        Path.of("/tmp/bookshelf-agent-example"), new HashSet<>()));
    }
}
