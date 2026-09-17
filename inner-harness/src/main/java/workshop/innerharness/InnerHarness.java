package workshop.innerharness;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The deliberately small part of a coding agent that participants complete.
 * Protocol-specific Anthropic code lives in {@link AnthropicSession}; tools live in
 * {@link BuiltinTools}. This class is only the model/tool conversation loop.
 */
public final class InnerHarness {
    public enum StopReason {
        COMPLETED,
        TURN_LIMIT_REACHED
    }

    public record ToolCall(String id, String name, Map<String, String> arguments) {
        public ToolCall {
            arguments = Map.copyOf(arguments);
        }
    }

    public record ToolResult(String toolCallId, String toolName, boolean succeeded, String content) {}

    public record ModelTurn(
            String text,
            List<ToolCall> toolCalls,
            long inputTokens,
            long outputTokens) {
        public ModelTurn {
            text = text == null ? "" : text;
            toolCalls = List.copyOf(toolCalls);
        }

        public static ModelTurn text(String text) {
            return new ModelTurn(text, List.of(), 0, 0);
        }

        public static ModelTurn tools(ToolCall... calls) {
            return new ModelTurn("", List.of(calls), 0, 0);
        }
    }

    public record RunResult(
            StopReason stopReason,
            int turns,
            String answer,
            long inputTokens,
            long outputTokens,
            List<String> trace) {
        public RunResult {
            trace = List.copyOf(trace);
        }
    }

    /**
     * A model conversation. Implementations own provider-specific message history.
     * The harness decides when to start it and when tool results go back into it.
     */
    public interface ModelSession {
        ModelTurn start(String task);

        ModelTurn continueWith(List<ToolResult> results);
    }

    public interface Tool {
        String name();

        Execution execute(Map<String, String> arguments);
    }

    public record Execution(boolean succeeded, String content) {
        public static Execution success(String content) {
            return new Execution(true, content);
        }

        public static Execution failure(String content) {
            return new Execution(false, content);
        }
    }

    public static final class ToolRegistry {
        private final Map<String, Tool> tools = new LinkedHashMap<>();

        public ToolRegistry register(Tool tool) {
            tools.put(tool.name(), tool);
            return this;
        }

        public ToolResult execute(ToolCall call) {
            var tool = tools.get(call.name());
            if (tool == null) {
                return new ToolResult(call.id(), call.name(), false,
                        "Unknown tool: " + call.name());
            }
            try {
                var execution = tool.execute(call.arguments());
                return new ToolResult(call.id(), call.name(), execution.succeeded(), execution.content());
            } catch (RuntimeException exception) {
                return new ToolResult(call.id(), call.name(), false,
                        "Tool failed: " + exception.getMessage());
            }
        }
    }

    private final ModelSession session;
    private final ToolRegistry tools;
    private final int maxTurns;

    public InnerHarness(ModelSession session, ToolRegistry tools, int maxTurns) {
        if (maxTurns < 1) throw new IllegalArgumentException("maxTurns must be positive");
        this.session = session;
        this.tools = tools;
        this.maxTurns = maxTurns;
    }

    public RunResult run(String task) {
        var trace = new ArrayList<String>();
        var turn = session.start(task);
        long inputTokens = 0;
        long outputTokens = 0;

        for (var turnNumber = 1; turnNumber <= maxTurns; turnNumber++) {
            inputTokens += turn.inputTokens();
            outputTokens += turn.outputTokens();
            trace.add("turn=" + turnNumber + " tool_calls=" + turn.toolCalls().size());

            // BONUS 1 — complete the inner loop.
            // 1. When there are no tool calls, return COMPLETED with this turn's text.
            // 2. Execute every tool call and add one trace line for each result.
            // 3. If another model turn is allowed, return those results to the model.
            // 4. Never make a model call after maxTurns has been reached.
        }

        trace.add("stop=turn_limit");
        return new RunResult(StopReason.TURN_LIMIT_REACHED, maxTurns, "",
                inputTokens, outputTokens, trace);
    }

    public static ToolRegistry defaultTools(
            Path workspace,
            Path skillRoot,
            BuiltinTools.ApprovalPolicy approvalPolicy) {
        var tools = new ToolRegistry()
                .register(new BuiltinTools.ReadFile(workspace))
                .register(new BuiltinTools.WriteFile(workspace, approvalPolicy));

        // BONUS 2 — register LoadSkill here. The model already knows that a
        // load_skill tool exists; until it is registered, dispatch reports it unknown.

        return tools;
    }
}
