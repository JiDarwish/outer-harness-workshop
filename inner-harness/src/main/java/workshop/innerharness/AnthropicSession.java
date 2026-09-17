package workshop.innerharness;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.BetaToolUseBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Supplied translation layer between the small workshop types and Anthropic's SDK.
 * Participants can read it, but neither bonus exercise requires changing it.
 */
// The SDK exposes tool inputs through a generic conversion API. Keep its unchecked
// cast warning at this provider boundary instead of leaking SDK types into the loop.
@SuppressWarnings("unchecked")
public final class AnthropicSession implements InnerHarness.ModelSession {
    private static final String SYSTEM_PROMPT = """
            You are a small coding agent working in an isolated workspace.
            Available skills: concise-java — concise conventions for reviewing Java.
            Load a relevant skill before applying it. Read files before discussing or changing them.
            Use relative paths. Ask for write permission when you want to create a file.
            """;

    private final com.anthropic.client.AnthropicClient client;
    private final MessageCreateParams.Builder conversation;
    private List<BetaContentBlockParam> lastAssistantBlocks = List.of();

    private AnthropicSession() {
        client = AnthropicOkHttpClient.fromEnv();
        conversation = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5)
                .maxTokens(4096)
                .system(SYSTEM_PROMPT)
                .addTool(ReadFile.class)
                .addTool(WriteFile.class)
                .addTool(LoadSkill.class);
    }

    public static AnthropicSession fromEnvironment() {
        return new AnthropicSession();
    }

    @Override
    public InnerHarness.ModelTurn start(String task) {
        conversation.addUserMessage(task);
        return requestTurn();
    }

    @Override
    public InnerHarness.ModelTurn continueWith(List<InnerHarness.ToolResult> results) {
        conversation.addAssistantMessageOfBetaContentBlockParams(lastAssistantBlocks);
        var resultBlocks = new ArrayList<BetaContentBlockParam>();
        for (var result : results) {
            resultBlocks.add(BetaContentBlockParam.ofToolResult(
                    BetaToolResultBlockParam.builder()
                            .toolUseId(result.toolCallId())
                            .contentAsJson(Map.of("output", result.content()))
                            .build()));
        }
        conversation.addUserMessageOfBetaContentBlockParams(resultBlocks);
        return requestTurn();
    }

    private InnerHarness.ModelTurn requestTurn() {
        var message = client.beta().messages().create(conversation.build());
        var text = new StringBuilder();
        var calls = new ArrayList<InnerHarness.ToolCall>();
        var replay = new ArrayList<BetaContentBlockParam>();

        for (var block : message.content()) {
            block.text().ifPresent(textBlock -> {
                if (!text.isEmpty()) text.append(System.lineSeparator());
                text.append(textBlock.text());
                replay.add(BetaContentBlockParam.ofText(
                        BetaTextBlockParam.builder().text(textBlock.text()).build()));
            });
            block.toolUse().ifPresent(toolUse -> {
                calls.add(toCall(toolUse));
                replay.add(BetaContentBlockParam.ofToolUse(
                        BetaToolUseBlockParam.builder()
                                .name(toolUse.name())
                                .id(toolUse.id())
                                .input(toolUse._input())
                                .build()));
            });
        }

        lastAssistantBlocks = List.copyOf(replay);
        var usage = message.usage();
        return new InnerHarness.ModelTurn(
                text.toString(), calls, usage.inputTokens(), usage.outputTokens());
    }

    private static InnerHarness.ToolCall toCall(BetaToolUseBlock toolUse) {
        var arguments = new LinkedHashMap<String, String>();
        switch (toolUse.name()) {
            case "read_file" -> arguments.put("path", toolUse.input(ReadFile.class).path);
            case "write_file" -> {
                var input = toolUse.input(WriteFile.class);
                arguments.put("path", input.path);
                arguments.put("content", input.content);
            }
            case "load_skill" -> arguments.put("name", toolUse.input(LoadSkill.class).name);
            default -> { /* The registry will produce an explicit unknown-tool result. */ }
        }
        return new InnerHarness.ToolCall(toolUse.id(), toolUse.name(), arguments);
    }

    @JsonClassDescription("Read a UTF-8 text file from the isolated workspace")
    public static final class ReadFile {
        @JsonPropertyDescription("Path relative to the workspace")
        public String path;
    }

    @JsonClassDescription("Write a UTF-8 text file in the isolated workspace; requires permission")
    public static final class WriteFile {
        @JsonPropertyDescription("Path relative to the workspace")
        public String path;
        @JsonPropertyDescription("Complete file content")
        public String content;
    }

    @JsonClassDescription("Load detailed instructions only when they are relevant. Available: concise-java")
    public static final class LoadSkill {
        @JsonPropertyDescription("Skill name, for example concise-java")
        public String name;
    }
}
