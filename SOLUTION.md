# Recovery guide

Reach for one section at a time, and rerun that section's command before reading on.
Getting unstuck is not the same as reading the answer to everything.

Section numbers match [GUIDE.md](GUIDE.md).

---

## 2. The borrowing policy is still a placeholder

Write the agreed outcome in your own words in `bookshelf/approved-policy.md`. It must
state these observable expectations:

- One physical copy can have at most one active loan.
- While Alice holds Book 1, Bob receives `BOOK_UNAVAILABLE` and Alice remains the active
  borrower.
- After Alice returns Book 1, Bob may borrow it and becomes the active borrower.

This is the human-approved guide. The test in the next section turns one example of that
rule into a computational sensor.

---

## 3. The assertions in `BorrowPolicyTest`

`bookshelf/src/test/java/workshop/bookshelf/BorrowPolicyTest.java`

Add these imports if your IDE does not add them automatically:

```java
import workshop.bookshelf.domain.BorrowOutcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
```

Replace the **entire test method** with the method below. In particular, remove the
starter's unasserted `service.borrow(1, aliceId)` call. Keeping it and then pasting the
assertions underneath would make Alice borrow once during setup and then incorrectly ask
her to borrow the same copy again.

```java
@Test
void aSecondMemberCannotBorrowAnAlreadyLoanedBook() {
    var shelf = BorrowServiceTest.shelf();
    var service = new BorrowService(shelf);
    var aliceId = 10;
    var bobId = 11;

    assertEquals(BorrowOutcome.BORROWED, service.borrow(1, aliceId));
    assertEquals(BorrowOutcome.BOOK_UNAVAILABLE, service.borrow(1, bobId));
    assertEquals((long) aliceId, shelf.activeLoan(1).memberId());

    assertEquals(BorrowOutcome.RETURNED, service.returnBook(1));
    assertEquals(BorrowOutcome.BORROWED, service.borrow(1, bobId));
    assertEquals((long) bobId, shelf.activeLoan(1).memberId());
}
```

```bash
./mvnw -pl bookshelf test                         # now FAILS on the starter defect
./mvnw -pl harness test -Dtest=BorrowPolicyControlTest  # both controls green
```

If the *good* control fails, read the assertion failure before touching production code.
Your test is probably rejecting behaviour the valid implementation never promised.

---

## 4. LAB 1a — the policy is not reaching the agent

```java
var prompt = task + "\n\nApproved policy:\n" + policy;
```

That is the whole change. The agent should implement the rule, not infer it.

---

## 4. LAB 1b — the check sequence

```java
var findings = new ArrayList<Finding>();
if (agent.failed()) {
    for (var spec : requiredChecks()) {
        findings.add(Finding.skipped(spec, "the agent produced no candidate to check"));
    }
    return List.copyOf(findings);
}

var compile = Checks.run(root, Checks.COMPILE);
findings.add(compile);
if (!compile.passed()) {
    for (var spec : requiredChecks()) {
        if (spec.name().equals(Checks.COMPILE.name())) continue;
        findings.add(Finding.skipped(spec,
                "compilation did not pass, so this cannot produce a verdict"));
    }
    return List.copyOf(findings);
}

var focusedAllPassed = true;
for (var spec : List.of(Checks.LINT, Checks.BUSINESS_BEHAVIOR,
        Checks.ARCHITECTURE_BOUNDARY)) {
    var finding = Checks.run(root, spec);
    findings.add(finding);
    if (!finding.passed()) focusedAllPassed = false;
}

findings.add(focusedAllPassed
        ? Checks.run(root, Checks.FULL_TEST_SUITE)
        : Finding.skipped(Checks.FULL_TEST_SUITE,
                "a focused check did not pass, so the broad suite adds nothing yet"));
return List.copyOf(findings);
```

Two things worth noticing in that code. The three focused checks run to completion even
after one of them fails — that is what "independent" means. And every stage that did not
run still produces a Finding, with a reason.

---

## 5. LAB 2a — `needsRepair`

```java
if (report.agent().failed()) return false;
var anyApplicationFailure = false;
for (var finding : report.findings()) {
    if (finding.error() || finding.unchecked()) return false;
    if (finding.failed()) anyApplicationFailure = true;
}
return anyApplicationFailure;
```

The early return on ERROR is the important line. An ERROR means no verdict was produced,
so there is no defect to describe — you would be asking the agent to fix your build tool.

---

## 5. LAB 2b — `repairPrompt`

```java
var prompt = new StringBuilder(task)
        .append("\n\nApproved policy:\n").append(policy)
        .append("\n\nEvery check below failed on the code you just wrote. ")
        .append("Fix all of them in one pass. Preserve the checks that already passed.\n");
for (var finding : report.findings()) {
    if (!finding.failed()) continue;
    prompt.append("\n- check: ").append(finding.name())
            .append("\n  intended property: ").append(finding.property())
            .append("\n  detail: ").append(finding.detail())
            .append("\n  reproduce with: ").append(finding.rerun()).append('\n');
}
return prompt.toString();
```

Note what is *not* here: the full Maven output. `finding.detail()` is already a compact
diagnostic, and that is deliberate. Keep the full logs locally; send the agent a bounded
summary.

The decision-scenario suite reads this prompt back and fails scenario 2 unless it
contains the policy and all three failing check names. Collect only the first failure
and you spend your whole budget fixing a third of the problem.

---

## 6. LAB 3 — `accepted`

```java
if (reports.isEmpty()) return false;
var last = reports.getLast();
if (last.agent().failed()) return false;
var passed = last.findings().stream().filter(Finding::passed).map(Finding::name).toList();
return requiredChecks().stream().allMatch(spec -> passed.contains(spec.name()));
```

`reports.getLast()` is doing the real work. Anything that looked at the whole list would
let a PASS from before the repair stand in for a result you never collected after it.

---

## The suite refuses to start

```
HARNESS VERIFICATION: STOP — replace the approved-policy placeholder first
```

`bookshelf/approved-policy.md` still has the starter questions. Finish section 2.

```
HARNESS VERIFICATION: STOP — finish BorrowPolicyTest and run its control pair first
```

Your borrowing check still passes against a known defect. Finish section 3. A loop
cannot be trusted until its business check rejects the bad control and accepts the good
control, so the suite declines to try.

---

## A scenario fails and you cannot see why

Run it alone and read the `@DisplayName`:

```bash
./mvnw -pl harness test -Dtest=OuterLoopBehaviourTest
```

The assertion messages say which decision was wrong, not just which value differed.
Common causes, in the order they usually bite:

- **Scenario 2 fails on the prompt** — you collected the first failing finding instead of
  all of them.
- **Scenario 3 leaves dependents unreported** — you returned early after `COMPILE`
  without emitting SKIPPED findings for the rest.
- **Scenario 4 accepts** — `accepted()` is looking across attempts rather than at the
  last one.
- **Scenario 6 repairs** — `needsRepair()` treats ERROR as a repairable failure.

---

## The complete live workflow

```bash
./harness.sh live claude
```

Read the headings as the architecture of the system:

```text
GUIDES BEFORE ACTION
  task.md + approved-policy.md

BUILD ATTEMPT — CLAUDE CODE
  visible Read/Edit activity from the inner harness

SENSORS AFTER ACTION
  evidence collected by your outer harness

OUTER-HARNESS DECISION
  accept, refuse, or spend the next repair from the configured budget

FRESH SENSORS AFTER ACTION
  shown only when a repair was requested

FINAL DECISION
```

A live model may finish with zero repairs because it got it right first time, use anything
up to `MAX_REPAIRS`, or stop without converging. None of those outcomes by itself proves
your loop right or wrong. The six supplied decision scenarios exercise its expected
control paths; this live run shows those decisions surrounding a real inner harness.

Claude works on an isolated production-only copy, then successful Java changes are copied
back to `bookshelf/src/main/java` before your sensors run. Open `BorrowService.java` after
the command if you want to inspect the candidate that received the final verdict.

---

## 8. BONUS — the inner-harness loop

This section belongs to the optional `inner-harness` module on the
`bonus-inner-harness` branch. Use one subsection at a time.

### The starter has five failing tests

That is deliberate. `InnerHarness.run` receives the first model turn but never acts on
it, so every scenario eventually reports `TURN_LIMIT_REACHED`.

```bash
./mvnw -pl inner-harness test
```

Do not change the scripted tests or the supplied Anthropic adapter. The same loop must
work with both.

### BONUS 1 — complete `run`

Replace `InnerHarness.run` with:

```java
public RunResult run(String task) {
    var trace = new ArrayList<String>();
    var turn = session.start(task);
    long inputTokens = 0;
    long outputTokens = 0;

    for (var turnNumber = 1; turnNumber <= maxTurns; turnNumber++) {
        inputTokens += turn.inputTokens();
        outputTokens += turn.outputTokens();
        trace.add("turn=" + turnNumber + " tool_calls=" + turn.toolCalls().size());

        if (turn.toolCalls().isEmpty()) {
            trace.add("stop=completed");
            return new RunResult(StopReason.COMPLETED, turnNumber, turn.text(),
                    inputTokens, outputTokens, trace);
        }

        var results = new ArrayList<ToolResult>();
        for (var call : turn.toolCalls()) {
            var result = tools.execute(call);
            results.add(result);
            trace.add("tool=" + call.name() + " result="
                    + (result.succeeded() ? "pass" : "fail"));
        }

        if (turnNumber == maxTurns) break;
        turn = session.continueWith(results);
    }

    trace.add("stop=turn_limit");
    return new RunResult(StopReason.TURN_LIMIT_REACHED, maxTurns, "",
            inputTokens, outputTokens, trace);
}
```

The order matters:

1. A response with no tool calls is the final answer.
2. Every tool call in a response is executed.
3. The results return together as the next observation.
4. At the limit, the harness does not make one extra model request.

Rerun the tests. Four should pass; skill loading remains red.

### BONUS 2 — register the skill loader

In `defaultTools`, extend the registry chain by one line:

```java
var tools = new ToolRegistry()
        .register(new BuiltinTools.ReadFile(workspace))
        .register(new BuiltinTools.WriteFile(workspace, approvalPolicy))
        .register(new BuiltinTools.LoadSkill(skillRoot));
```

Then run:

```bash
./mvnw -pl inner-harness test
```

Expected checkpoint:

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The adapter advertised only the skill's name and purpose. The complete file entered the
conversation when the model requested `load_skill`; that is the progressive-disclosure
mechanism you just connected.

### The live run cannot find credentials

The deterministic bonus is already complete. A live run additionally requires an
Anthropic API key in the same terminal:

```bash
export ANTHROPIC_API_KEY=your-key
./mvnw -pl inner-harness compile exec:java
```

Authentication through Claude Code does not automatically create
`ANTHROPIC_API_KEY` for the Java SDK.

### The model asks to write, but no file appears

`write_file` is permission-gated. Enter `y` when the process asks:

```text
Allow write_file for Review.md? [y/N]
```

Anything else returns a failed tool result to the model and leaves the workspace
unchanged. That denial is part of the conversation rather than an invisible exception.

### The live run reaches the turn limit

This is a valid controlled stop, not acceptance. Inspect the trace to see whether the
model repeatedly requested a tool, received a failed result, or never produced a final
answer. The inner harness ends the invocation; it does not decide whether generated work
is acceptable.
