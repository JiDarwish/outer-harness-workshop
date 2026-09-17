# Guide

Work through these in order. Each section starts with the idea, because the point of
today is not the code — it is being able to defend every decision the code makes.

---

## 1. Notice the false green

### The idea

Every decision your outer loop will make reads the output of a check. So the first
question is not "is my code correct" but **"is my check capable of telling me?"**

A suite can run, pass, and still be silent about the thing you actually care about. A
green build only tells you that the assertions which ran were satisfied. It says nothing
about a business rule that no assertion expressed.

### Do this

```bash
./mvnw -pl bookshelf test
```

It passes. Now open two files:

- `bookshelf/src/main/java/workshop/bookshelf/service/BorrowService.java`
- `bookshelf/src/test/java/workshop/bookshelf/BorrowPolicyTest.java`

`borrow()` never checks whether the book is already on loan, so Bob can take the copy
Alice is holding. And `BorrowPolicyTest` calls `borrow` once and asserts nothing at all.

**Ask what is missing from the test, not how to prompt a model harder.**

---

## 2. Settle the rule before anything runs

### The idea

Böckeler splits harness controls along two axes. **Guides** are feedforward: they steer
the agent *before* it acts. **Sensors** are feedback: they observe *after*, so it can
self-correct. Crossing that, controls are **computational** (deterministic, fast, decided
by the machine) or **inferential** (somebody, or something, has to interpret).

The borrowing rule is an *inferential guide*. A person has to decide what correct means
and be accountable for it. No tool can do that for you, and pasting the file into a
prompt mechanically does not change what kind of control it is.

### Do this

Decide it as a room, then write it into `bookshelf/approved-policy.md`:

- While a copy is on loan, what does a second request return, and who holds the copy?
- After it is returned, what does the next request return, and who holds it then?

Replace the starter questions with the answers. Specification is what the librarian
agreed; conformance is what a check can verify. Only one of those can be automated.

> Source: [Böckeler, *Harness engineering for coding agent users*](https://martinfowler.com/articles/harness-engineering.html)

---

## 3. Make the rule executable, then check the checker

### The idea

Now the same rule becomes a *computational sensor*. But a test you have never seen fail
is not yet evidence. So you challenge the test with a pair: one implementation it must
reject, one it must accept.

### Do this

Write the assertions in `BorrowPolicyTest`. The starter contains this unasserted setup
call:

```java
service.borrow(1, aliceId);
```

**Replace that call** with an assertion that Alice's first borrow succeeds. Do not leave
the original call above your assertions, or the test will accidentally ask Alice to
borrow the same copy twice. Then assert Bob's result and the active borrower while Alice
holds the copy, return it, let Bob borrow, and assert both again.

```bash
./mvnw -pl bookshelf test        # BorrowPolicyTest should now FAIL on the starter defect
./mvnw -pl harness test -Dtest=BorrowPolicyControlTest
```

**Checkpoint:** both control tests green — your check rejects the known defect and
accepts a valid implementation. A test that still passes the bad control is not yet a
sensor.

This is a cheap control pair, not mutation testing. It is no evidence that the business
rules are complete, and you should say so out loud.

---

## 4. LAB 1 — the guide, then the sequence

`harness/src/main/java/workshop/harness/OuterHarness.java`

### The idea

Two things, and they are the two halves of Böckeler's model.

First the **guide**: the agreed rule has to reach the agent before it writes anything.
Right now the prompt is just `task.md`, so the agent is being asked to implement a policy
it has never seen.

Then the **sensors**: five named checks, and an order. Ordering is a judgement you make
and defend. Put the cheap gate first — a boot-probe study found a single cheap check
eliminated nearly all launch failures at about a third of the cost of a full shell — and
stop before the expensive one when something has already failed.

The rule that is not negotiable: **a non-PASS result must never hide another independent
finding**, and a stage that did not run must say so. Silence is not a pass.

### The small API you need

The plumbing is supplied. These are the methods you need to express the decisions in
Labs 1–3; you do not need to discover another API before you start:

```java
report.agent().failed()                 // the agent produced no candidate
report.findings()                       // findings from this exact attempt

finding.passed()                        // PASS
finding.failed()                        // FAIL
finding.error()                         // ERROR
finding.unchecked()                     // UNCHECKED

finding.name()                          // named check, such as BUSINESS_BEHAVIOR
finding.property()                      // the property that check protects
finding.detail()                        // short failure diagnostic
finding.rerun()                         // exact reproduction command

requiredChecks()                        // every check acceptance requires
spec.name()                             // the name of one required check
```

### Do this

1. Build `prompt` from `task.md` **and** `approved-policy.md`.
2. Implement `checkSequence`:
   - no candidate from the agent → SKIP every required check, with a reason
   - `COMPILE` first; on FAIL or ERROR, SKIP the dependents and return
   - `LINT`, `BUSINESS_BEHAVIOR`, `ARCHITECTURE_BOUNDARY` independently
   - `FULL_TEST_SUITE` only if compilation and all three focused checks passed

Use `Checks.run(root, spec)` and `Finding.skipped(spec, reason)`.

`LINT` runs Checkstyle with five deliberately small rules: no unused imports, wildcard
imports, empty catch blocks, multiple statements on one line, or tab characters. It is a
cheap mechanical sensor, not evidence that the business behaviour is correct.

```bash
./harness.sh check
```

**Checkpoint:**

```
━━ SENSORS ON CURRENT SOURCE ━━
PASS      COMPILE
PASS      LINT
FAIL      BUSINESS_BEHAVIOR
PASS      ARCHITECTURE_BOUNDARY
SKIPPED   FULL_TEST_SUITE

━━ FINAL DECISION ━━
UNRESOLVED
Attempts: 1
Repairs: disabled in check-only mode
```

It exits 1 on purpose. Check-only invokes no agent and never requests a repair: these
checks ran against the source exactly as it stands. `FULL_TEST_SUITE` never ran, and that
is the ordering paying for itself.

> Source: [Mehta, *The reach of a verification tool decides its value*](https://arxiv.org/abs/2608.28795)

---

## 5. LAB 2 — bound the repair, and make it useful

`needsRepair()` and `repairPrompt()`

### The idea

This is the part people get wrong, and there is now evidence for how wrong.

Gao, Yang and Yang measured agentic repair loops under forced revision: correctness
**fell from 0.820 after one revision to 0.673 after two**, while "ever-correct" reached
0.847. The loop finds the right answer and then loses it. Stale verification traces
harmed 34 of 135 initially-correct attempts, against 4 of 135 with current traces.

**Looping is not reliability.** A repair budget is not stinginess, it is the control that
stops a loop from thrashing away a correct answer.

The second half is what goes *in* the prompt. This is where feedback becomes the next
feedforward: sensor output, rewritten as a guide. Böckeler's strongest practical finding
is that a sensor should not merely report an error but teach recovery — say why the rule
exists and what a good solution looks like. Compact diagnostics are what made repair work
in her field experience, not more output.

### Do this

Near the top of `OuterHarness.java`, the supplied loop declares its budget:

```java
static final int MAX_REPAIRS = 1;
```

The workshop keeps it at one so every run is short. You can change that single value to
`3` if you want to allow up to three repairs. The loop stops earlier when the candidate
passes, the agent fails, or a sensor cannot produce a trustworthy verdict. The limit is a
maximum, not a target.

- `needsRepair`: application FAIL findings only. An agent failure, a check ERROR, or
  UNCHECKED evidence means there is no trustworthy defect to repair against.
- `repairPrompt`: **one** prompt carrying the approved policy and **every** failing
  finding — purpose, intended property, one diagnostic line, and the exact rerun command.

Three failures do not earn three repairs. They share this prompt.

There is no separate runtime checkpoint here because check-only never invokes an agent.
Lab 3 completes the decision rule, then the supplied scenarios validate the repair
decision, repair prompt, fresh rechecking, and final acceptance together.

> Source: [Gao, Yang and Yang, *Looping Is Not Reliability*](https://arxiv.org/abs/2607.24604)

---

## 6. LAB 3 — decide acceptance on the final attempt

`accepted()`

### The idea

Two green labels can exist and still mean nothing, because they belong to two different
versions of the source. Accumulating green across attempts is the easiest way to build a
loop that accepts broken work — and it is exactly the stale-evidence failure the paper
above measured.

### Do this

Inspect the **final** attempt only. It must come from a successful agent call and carry a
complete set of PASS findings. Never reuse a PASS from before a repair.

```bash
./mvnw -pl harness test
```

The supplied tests use deterministic agents and disposable Bookshelf copies. They
challenge the decisions you implemented without requiring a live model: when to accept,
when to repair, and when to stop without accepting.

**Checkpoint:** all six supplied decision scenarios pass.

| | Scenario | Ends |
|---|---|---|
| 1 | Accept a valid first attempt | ACCEPTED, 0 repairs |
| 2 | Repair all independent failures | ACCEPTED, 1 repair |
| 3 | Stop dependent checks after compilation fails | ACCEPTED, 1 repair |
| 4 | Reject a regression introduced by repair | UNRESOLVED, 1 repair |
| 5 | Stop when the agent produces no candidate | UNRESOLVED, 0 repairs |
| 6 | Do not repair broken check infrastructure | UNRESOLVED, 0 repairs |

Three accept and three refuse, and that balance is the point. A loop that refuses
everything passes no test worth passing.

The suite will not start until your policy placeholder is gone and your business check
has passed the control pair. It declines to certify a loop whose business check has never
been shown to reject the known defect and accept the valid implementation.

---

## 7. Capstone — see the complete workflow

```bash
./harness.sh live claude     # or: ./harness.sh live codex
```

This connects the outer harness you wrote to a real inner harness:

```text
task.md + approved-policy.md
             ↓
       Claude Code
       (inner harness)
             ↓
         candidate
             ↓
         your sensors
             ↓
   your evidence and decision
```

Watch the boundaries in the terminal. It first names the guides sent before action.
Claude Code then streams the production files it reads and edits. Only after Claude
finishes do your sensors run. If their evidence justifies a repair, the outer harness
sends one focused prompt and collects a fresh set of findings before deciding.

The adapter gives Claude an isolated copy containing production Java only. It cannot
inspect or edit your tests or harness. A successful candidate is copied back to
`bookshelf/src/main/java` before the sensors run, so you can open the changed source when
the command finishes.

The adapters are pinned to Claude `haiku` and Codex `gpt-5.5`. They deliberately avoid
each provider's strongest model so the surrounding guidance and feedback have a chance
to become visible.

A live model may use anything from zero repairs up to `MAX_REPAIRS` — it may simply get it
right first time. Do not force a failure just to show the retry. The six supplied
decision scenarios exercise the expected control paths; the live run is the eye test
that connects those decisions to an actual inner harness.

---

## What transfers

Four parts, none of them model-specific or Java-specific.

- **An approved guide** — a rule a person agreed, reaching the agent before it acts. At
  work this is your pricing rule, your permission model, your retention policy.
- **An independent sensor bank** — named checks the agent cannot read or edit. A check
  the agent can edit is not a check.
- **A bounded repair** — one attempt, spent only on findings worth repairing, fed by
  compact diagnostics rather than raw tool output.
- **A fresh verdict** — acceptance decided on the final source only.

The Bookshelf was a prop. Those four slots are the deliverable.

Before you leave, name one recurring failure in your own repository and fill in all
eight: the failure, the guide you would agree, the sensor you would run, one known-bad
control, one valid control, the repair budget, what you would measure, and **the human
decision that stays with a person**.

If you cannot name that last one, the loop is not finished. It is just unattended.

---

## 8. BONUS — build the inner harness

Finished the main workshop early? This branch contains a third Maven module that opens
the coding agent we treated as a black box. You will implement the small conversation
loop that turns a model with tools into an agent.

Allow about 20–30 minutes. The deterministic exercises need no model account or API key.

### Keep the boundary clear

The main workshop built `harness/`: guides, sensors, bounded repair and the final decision
around a complete coding agent. This bonus builds `inner-harness/`: conversation state,
tool dispatch, permissions, stopping conditions and on-demand context inside one agent
invocation.

```text
task
  ↓
model response
  ↓
tool call? ── no ──→ stop
  │
 yes
  ↓
permission and dispatch
  ↓
tool result
  └────────────────→ model again
```

Keep Bookshelf policy, business tests, architecture checks, repair prompts and acceptance
decisions out of this module. Those belong to the outer harness.

### Find the seam

Open these three files:

- `inner-harness/src/main/java/workshop/innerharness/InnerHarness.java` — your exercise
- `inner-harness/src/main/java/workshop/innerharness/AnthropicSession.java` — supplied
  provider translation
- `inner-harness/src/main/java/workshop/innerharness/BuiltinTools.java` — supplied tool
  implementations and path/permission boundaries

`ModelSession` hides Anthropic's protocol types, but it does not make decisions for the
loop. `start` sends the task. `continueWith` appends the assistant turn and tool results
to the same conversation before asking the model again.

Run the starter tests:

```bash
./mvnw -pl inner-harness test
```

All five should be red. Read the test names: each one describes a responsibility that is
still missing from the inner loop.

### BONUS 1 — complete the model/tool loop

Work only in `InnerHarness.run`.

For every model turn:

1. Accumulate its token counts and record the turn — the starter already does this.
2. If it contains no tool calls, stop with `COMPLETED`, the current turn number and the
   model's text.
3. Otherwise execute **every** call through `tools.execute(call)`.
4. Add a trace line such as `tool=read_file result=pass` or `result=fail`.
5. If the turn budget is now exhausted, stop without another model request.
6. Otherwise call `session.continueWith(results)` and repeat.

Keep one list of results per turn. A useful shape is:

```java
var results = new ArrayList<ToolResult>();
for (var call : turn.toolCalls()) {
    var result = tools.execute(call);
    results.add(result);
    // Record the result in trace.
}
```

The turn budget counts model responses. Tool execution does not secretly grant another
model call.

```bash
./mvnw -pl inner-harness test
```

**Checkpoint:** four tests pass. Only
`loadsSkillContentOnlyWhenTheModelRequestsIt` should still fail.

### BONUS 2 — add progressive skill loading

A skill has two parts:

- small metadata tells the model what knowledge is available;
- the full `SKILL.md` enters the conversation only if the model calls `load_skill`.

That is progressive disclosure. It avoids putting every optional instruction into every
request.

The Anthropic adapter already advertises `concise-java`, and `BuiltinTools.LoadSkill`
already reads a requested skill safely. In `InnerHarness.defaultTools`, register one
`LoadSkill` instance alongside the read and write tools. Use the supplied `skillRoot`.

```bash
./mvnw -pl inner-harness test
```

**Checkpoint:** all five tests pass.

The loading mechanism belongs to the inner harness. The contents of a real company skill
are guidance supplied by its users. This exercise implements the mechanism and uses a
small neutral skill only to prove that it works.

### Optional — use the real Anthropic API

The deterministic tests are the completion criterion. If you also have an Anthropic API
key, run the same loop against Claude Haiku:

```bash
export ANTHROPIC_API_KEY=your-key
./mvnw -pl inner-harness compile exec:java
```

The demo creates `inner-harness/target/live-workspace/Example.java`. The model should load
the skill, read the file and ask before writing `Review.md`. The generated file remains
under `target/` and is not part of the repository.

Watch for four things:

- more than one model turn;
- `load_skill` and `read_file` results returning into the conversation;
- a human permission boundary before `write_file`;
- a visible stopping reason and token total.

Live output is variable. A model may choose different tools or decline to write. The
scripted tests establish the loop behaviour; the live run only lets you observe it.

### Debrief

You have now built the beginning of a coding agent:

```java
while (turnBudgetRemains()) {
    var response = model.respond(conversation);
    if (response.isFinal()) return response;
    conversation.add(execute(response.toolCalls()));
}
```

The model proposes. The inner harness preserves state, controls available actions,
executes tools, returns observations and decides when one invocation must stop. The
outer harness then decides whether the resulting work is acceptable.
