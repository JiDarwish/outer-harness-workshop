# Slide 1 — Harness Engineering

## Build the outer loop around a coding agent

**A 95-minute Java workshop**

Guide the work. Run independent sensors. Repair with evidence. Accept only fresh results.

---

# Slide 2 — Before we start

From the repository root:

```bash
bash preflight.sh
```

You need:

- Java 25
- JBang
- Git
- The included Maven wrapper

Native-access and SLF4J warnings are expected. Use the test result and labelled checkpoints.

---

# Slide 3 — This is a workshop with a little theory

We will use:

- Market signals from teams building with coding agents
- Practitioner experience from the field
- Recent research on harnesses, specifications, tests, and feedback
- A small Java system where we can make the ideas executable

Every theory section should answer:

> What decision will this change in the code we write today?

---

# Slide 4 — What you will build

By the end, your outer harness will:

1. Give an agent an approved business rule
2. Record every agent invocation and check each produced candidate
3. Turn failures into one bounded repair request
4. Stop when the evidence is unreliable
5. Recheck the repaired source from scratch
6. Accept only fresh, complete evidence

The main exercise is the **decision loop**, not another agent prompt.

---

# Slide 5 — A model is not an agent

```text
AGENT = MODEL + HARNESS
```

The harness supplies things such as:

- Context and instructions
- Tools and filesystem access
- A loop for model actions and tool results
- Timeouts and budgets
- Stopping and completion rules

Changing the harness changes the system, even when the model stays the same.

---

# Slide 6 — One signal: Terminal-Bench

Same model, different agent harnesses in one benchmark:

| Model | Harness | Resolution rate |
|---|---|---:|
| Claude Opus 4.5 | Terminus 2 | 57.8% |
| Claude Opus 4.5 | Claude Code | 52.1% |

This is evidence that the surrounding system matters.

It is **not** a universal harness ranking or a dollar-cost comparison.

Source: [Merrill et al., *Terminal-Bench*](https://arxiv.org/html/2601.11868v1)

---

# Slide 7 — A simplified inner loop

```text
while (task remains) {
    action = model(context)
    result = tools.run(action)
    context += result
}
```

The vendor's coding agent already owns a loop like this:

- Ask the model what to do
- Run a tool
- Return the result to the model
- Decide when to stop

*Conceptual pseudocode — not vendor source code.*

---

# Slide 8 — Inner harness and outer harness

```text
┌──────────────────── OUTER HARNESS ────────────────────┐
│ approved intent · independent checks · repair budget  │
│ stopping rules · acceptance · evidence                │
│                                                       │
│   ┌────────────── INNER HARNESS ────────────────┐     │
│   │ model · context · tools · action loop       │     │
│   └─────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────┘
```

Today:

- The provider supplies the inner harness
- The repository supplies process plumbing
- **You write the outer decision loop**

---

# Slide 9 — The boundary in this workshop

The coding agent may edit:

```text
bookshelf/src/main/java/
```

The agent cannot edit or read:

```text
bookshelf/src/test/java/
bookshelf/approved-policy.md
```

This protects an independent acceptance sensor for the experiment. It is not a
general rule against agent-authored tests. In a real repository, an agent may
write development tests while protected acceptance checks and CI policy remain
outside its control.

Your outer harness owns:

- What policy enters the prompt
- Which checks run
- Which findings enter a repair request
- Whether the result is accepted

---

# Slide 10 — Böckeler's harness model

Two directions of influence:

```text
GUIDES ──────── before action ────────▶ CODING AGENT

SENSORS ◀────── after action ───────── changed system
```

- **Guides** increase the chance of a useful first attempt
- **Sensors** make properties visible after the attempt
- Feedback can support self-correction before human review

Source: [Birgitta Böckeler, *Harness engineering for coding agent users*](https://martinfowler.com/articles/harness-engineering.html)

---

# Slide 11 — A second axis: how is the control evaluated?

| | Guide: before action | Sensor: after action |
|---|---|---|
| **Computational** | Language and tool constraints | Compiler, linter, JUnit, ArchUnit |
| **Inferential** | Principles, domain explanation | Model review, human judgement |

Important distinction:

- Agreeing what Bob **should** be allowed to do requires judgement
- Running the resulting JUnit assertion is computational
- A test can be created inferentially and executed computationally

Source: [Böckeler, *Harness engineering for coding agent users*](https://martinfowler.com/articles/harness-engineering.html)

---

# Slide 12 — Böckeler's sensor lesson

In one TypeScript application, Böckeler experimented with:

- Type checking, linting, dependency rules, tests, and mutation testing
- Compact sensor output designed for repair
- Slower inferential security and modularity reviews
- Different sensor cadences during coding, in CI, and over time

She also found that asking agents to remember to check sensors was unreliable.

**Our design inference:** the outer loop invokes the checks itself and withholds acceptance until they pass.

Source: [Böckeler, *Maintainability sensors for coding agents*](https://martinfowler.com/articles/sensors-for-coding-agents.html)

---

# Slide 13 — The Bookshelf

The domain is deliberately small:

- One physical copy of each book
- Alice is member `10`
- Bob is member `11`
- They both want Book `1`

The starter has a known defect:

> Bob can replace Alice as the active borrower while Alice still holds the book.

The existing test suite is green.

---

# Slide 14 — Calibration: notice the false green

Run from the repository root:

```bash
(cd bookshelf && ./mvnw -B test)
```

Open:

```text
bookshelf/src/main/java/workshop/bookshelf/service/BorrowService.java
bookshelf/src/test/java/workshop/bookshelf/BorrowPolicyTest.java
```

Ask:

> Why does the test suite pass when Bob can replace Alice's loan?

**Checkpoint:** Maven passes, but `BorrowPolicyTest` has no useful assertions.

---

# Slide 15 — First settle the rule

The sequence:

```text
1. Alice borrows Book 1
2. Bob asks for Book 1
3. Alice returns Book 1
4. Bob asks again
```

Decide with the librarian:

- What result should Bob receive at step 2?
- Who should remain the active borrower?
- What should happen at step 4?
- Who should be the active borrower then?

The agent should implement this decision, not invent it.

---

# Slide 16 — Specification and conformance are different

```text
SPECIFICATION
What behaviour do we intend?
        │
        ▼
CONFORMANCE CHECK
Does this implementation satisfy the stated example?
```

A passing check can show conformance to a mistaken expectation.

Human approval is accountable evidence of intent. It is not proof that the human is infallible or that one example covers the domain.

Related sources: [Lahiri, *Intent Formalization*](https://arxiv.org/html/2603.17150v1) · [Haeri and Ghelichi, *Specification Grounding Drives Test Effectiveness*](https://arxiv.org/html/2607.06636v1)

---

# Slide 17 — Calibration A: write the guide

Edit:

```text
bookshelf/approved-policy.md
```

Replace the questions with the agreed outcome.

Make the policy concrete enough to answer:

- Bob's result while Alice holds the book
- The active borrower before the return
- Bob's result after the return
- The active borrower afterward

This file is the **guide before action**.

---

# Slide 18 — Calibration B: make the rule executable

Edit:

```text
bookshelf/src/test/java/workshop/bookshelf/BorrowPolicyTest.java
```

Your test should:

1. Use the supplied shelf, service, and Alice ID
2. Turn Alice's unasserted first borrow into an assertion
3. Add Bob's ID and assert his result while Alice holds Book 1
4. Assert the active borrower is still Alice
5. Return Book 1
6. Assert Bob can now borrow it
7. Assert the active borrower is Bob

This JUnit test is the **sensor after action**.

---

# Slide 19 — Checkpoint: the starter must become red

Run:

```bash
(cd bookshelf && ./mvnw -B -Dtest=BorrowPolicyTest test)
```

Expected result:

```text
FAIL
expected: BOOK_UNAVAILABLE
 but was: BORROWED
```

If the starter still passes, the new assertions do not detect the known defect yet.

---

# Slide 20 — A sensor needs controls too

Run:

```bash
bash controls.sh
```

Target checkpoint:

```text
BAD CONTROL: FAIL — the new check detects the defect
GOOD CONTROL: PASS — the check accepts valid borrowing
CONTROL PAIR: PASS
```

The script temporarily runs the same test against:

- A known defective service
- A known valid service

It restores the starter production file afterward.

---

# Slide 21 — What the borrowing exercise represents

```text
Discuss an ambiguous domain rule
              ↓
Agree on concrete examples
              ↓
Encode the examples as executable checks
              ↓
Challenge the checks with bad and valid controls
```

This is a small instance of behaviour-driven development.

At work, the rule might concern pricing, permissions, time zones, idempotency, or data retention.

The lesson is **specification content and oracle quality**, not merely “write more tests.”

---

# Slide 22 — Now build the outer loop

Open:

```text
harness/OuterHarness.java
```

You own five decisions:

| LAB | Decision |
|---|---|
| 1 | What guidance goes into the initial prompt? |
| 2 | Which sensors run, and when are they skipped? |
| 3 | When do we repair, and what feedback is sent? |
| 4 | What exact evidence permits acceptance? |
| 5 | What evidence does the report expose? |

`Agent.java` and `Checks.java` supply the process plumbing.

---

# Slide 23 — The outer loop we are building

```text
build with approved policy
          │
          ▼
run named checks ───────────────┐
          │                     │
          ├── all PASS ──▶ ACCEPT
          │                     │
          ├── app FAIL ──▶ one repair
          │                     │
          └── ERROR ─────▶ STOP │
                                │
                 rerun all checks
                 on repaired source
```

One repair keeps the workshop bounded. The important part is that the budget is explicit.

---

# Slide 24 — Five named checks

| Check | Property |
|---|---|
| `COMPILE` | Production Java compiles |
| `STATIC_HYGIENE` | Library code does not write directly to the console |
| `BUSINESS_BEHAVIOR` | The approved borrowing policy |
| `ARCHITECTURE_BOUNDARY` | Domain code does not depend on service or storage |
| `FULL_TEST_SUITE` | All existing Bookshelf tests still pass |

Names express **purpose**. `Checks.java` maps each purpose to a concrete command.

---

# Slide 25 — Five result states

| State | Meaning | Repair the application? | Accept? |
|---|---|---:|---:|
| `PASS` | The property check completed successfully | No | Yes, for this attempt |
| `FAIL` | Recognised code, test, compile, or lint violation | Maybe | No |
| `ERROR` | The check did not produce trustworthy evidence | No — investigate | No |
| `SKIPPED` | Deliberately not run; reason recorded | No | No |
| `UNCHECKED` | The participant has not wired the stage | No | No |

A non-zero process exit is not automatically an application defect.

---

# Slide 26 — LAB 1: guide before action

Current starter:

```java
var task = Files.readString(root.resolve("harness/task.md"));
var policy = Files.readString(root.resolve("bookshelf/approved-policy.md"));

// LAB 1 — include the approved policy
var prompt = task;
```

Change the initial prompt so the agent receives:

- The implementation task
- The approved borrowing policy

Keep the policy in the repair prompt too. A repair must not silently redefine the requirement.

---

# Slide 27 — LAB 2: check sequence

Implement `checkSequence`:

```text
COMPILE
  ├── FAIL/ERROR → dependent stages SKIPPED
  └── PASS
       ├── STATIC_HYGIENE
       ├── BUSINESS_BEHAVIOR
       └── ARCHITECTURE_BOUNDARY
             │
             ├── all PASS → FULL_TEST_SUITE
             └── any non-PASS → suite SKIPPED
```

Use:

```java
Checks.run(root, spec)
Finding.skipped(spec, reason)
```

Keep the compile gate explicit:

```java
if (!compile.passed()) {
    var reason = "Compilation did not pass";
    var checksBlockedByCompilation = List.of(
            Checks.STATIC_HYGIENE,
            Checks.BUSINESS_BEHAVIOR,
            Checks.ARCHITECTURE_BOUNDARY,
            Checks.FULL_TEST_SUITE);
    for (var check : checksBlockedByCompilation) {
        findings.add(Finding.skipped(check, reason));
    }
    return findings;
}
```

The named list shows which sensors depend on compilation. There is no list
index or repeated skip statement to interpret.

Every agent invocation gets a report. Sensors run only when it produced a
candidate source tree.

---

# Slide 28 — Why this ordering?

1. **Compile is a prerequisite**
   - Tests and syntax-based checks need valid production source
   - A compile failure gives focused repair evidence

2. **The focused checks are independent**
   - A `FAIL` or `ERROR` from one does not hide the others
   - One repair request can contain all current failures

3. **The full suite is conditional**
   - Run it only when the focused properties pass
   - It is the broad regression check for existing behaviour

Fast checks early can reduce wasted work when they fail. This workshop does not claim one universal ordering between JUnit and ArchUnit.

---

# Slide 29 — The early static sensor is deliberately narrow

`STATIC_HYGIENE` checks one rule:

> Production library code must not write directly to `System.out` or `System.err`.

Why only one rule?

- It has a known violating fixture and a known valid fixture
- Its diagnostic includes a file, line, reason, and repair direction
- It is fast in the local pilot
- It avoids turning the workshop into a linter configuration exercise

More warnings are not automatically more quality.

Source: [Böckeler, *Maintainability sensors for coding agents*](https://martinfowler.com/articles/sensors-for-coding-agents.html)

---

# Slide 30 — Checkpoint: wire the guide and sensors

Run:

```bash
jbang harness/OuterHarness.java --agent=noop
```

The no-op agent changes nothing.

Expected after LAB 1 and LAB 2:

```text
COMPILE                 PASS
STATIC_HYGIENE          PASS
BUSINESS_BEHAVIOR       FAIL
ARCHITECTURE_BOUNDARY   PASS
FULL_TEST_SUITE         SKIPPED
status=UNRESOLVED repairs=0
```

The command exits with status `1` on purpose.

---

# Slide 31 — LAB 3A: decide whether to repair

Implement `needsRepair`.

Repair only when:

- The agent attempt succeeded
- At least one current finding is `FAIL`
- There is no `ERROR` or `UNCHECKED` evidence
- The one-repair budget is still available

Do not ask the coding agent to repair:

- A failed agent invocation
- A sensor timeout or infrastructure error
- A check that has not been wired

Those failures do not establish an application defect.

Use ordinary `if` statements and a loop: return early for an agent failure,
`ERROR`, or `UNCHECKED`; remember whether at least one `FAIL` was found.

---

# Slide 32 — LAB 3B: make feedback useful

Build one bounded repair prompt containing:

```text
Original task
Approved policy

For every current FAIL:
  - check purpose
  - intended property
  - short diagnostic
  - exact rerun command
```

Example shape:

```text
BUSINESS_BEHAVIOR — the approved borrowing policy
expected BOOK_UNAVAILABLE but was BORROWED
rerun: cd bookshelf && ./mvnw ... BorrowPolicyTest test
```

Keep full logs on disk. Send the compact evidence needed for repair.

---

# Slide 33 — Why collect all focused failures?

Suppose one source snapshot has:

```text
BUSINESS_BEHAVIOR       FAIL
ARCHITECTURE_BOUNDARY   FAIL
```

With a one-repair budget:

- First-failure-only feedback may fix borrowing and leave structure unseen
- Combined feedback lets the repair address both known violations

The goal is not “show the agent everything.”

The goal is “show every **relevant, current, calibrated** failure.”

---

# Slide 34 — LAB 4: define acceptance

```text
ACCEPTED = final agent attempt succeeded
        + final findings are complete
        + every final finding is PASS
```

Implement `accepted(reports)` so it verifies:

- The final attempt produced a candidate
- The final finding count matches `requiredChecks()`
- Every final finding is `PASS`

Never combine green results from different source snapshots.

---

# Slide 35 — Freshness is part of correctness

| Check | Build attempt | Repair attempt |
|---|---:|---:|
| Business behaviour | `FAIL` | `PASS` |
| Architecture boundary | `PASS` | `FAIL` |
| Full test suite | `SKIPPED` | `SKIPPED` |

The repair fixed borrowing and introduced a dependency violation.

Final decision:

```text
UNRESOLVED
```

An old architecture `PASS` cannot approve new source code.

---

# Slide 36 — Reporting is supplied

The starter reports:

- Attempt label and agent outcome
- Elapsed time and available token categories
- Every check name and state
- Failure details and skipped reasons
- Full local log paths

Read this as the evidence behind `ACCEPTED` or `UNRESOLVED`. Coding the output
format is not part of the four active labs.

<!-- OPTIONAL LAB 5 — preserve for a later workshop decision.

# Optional LAB 5: report the evidence

For each attempt, show:

- Attempt label: build or repair
- Agent result and elapsed time
- Available input, output, and cached-input token categories
- Every check name, state, and elapsed time
- Failure details or skipped reason
- Full local log path

Final line:

```text
status=ACCEPTED|UNRESOLVED|UNCHECKED repairs=N elapsed_ms=N
```

Missing usage is `unavailable`, not zero.

End optional LAB 5. -->

---

# Slide 37 — Checkpoint: the no-op remains unresolved

Run again:

```bash
jbang harness/OuterHarness.java --agent=noop
```

Expected:

- Build attempt: business `FAIL`
- One repair request containing that failure
- Repair attempt: fresh business `FAIL`
- Architecture remains `PASS`
- Full test suite remains `SKIPPED`
- Final status: `UNRESOLVED`

```text
status=UNRESOLVED repairs=1
```

Rejecting a bad result is a successful harness outcome.

---

# Slide 38 — Test the harness itself

Run:

```bash
bash verify-harness.sh
```

This script:

- Rechecks the borrowing oracle first
- Creates disposable project copies
- Uses deterministic scripted agents, not a live model
- Exercises successful and failing control paths
- Leaves your working production sources unchanged

Target checkpoint:

```text
HARNESS CONTROL SUITE: PASS
```

Expect roughly one minute with warm local caches.

Think of this as the test suite for the outer harness you wrote. You do not need
to understand its Bash implementation; each labelled scenario tells you which
decision in your loop is being challenged.

---

# Slide 39 — What the harness verification challenges

| Scripted case | Decision your loop must make |
|---|---|
| Valid first attempt | Accept with zero repairs |
| Unchanged defect | Repair once, then remain unresolved |
| Fixed on repair | Accept fresh passing evidence |
| Behaviour green, structure wrong | Reject |
| Both properties fail | Include both in one repair prompt |
| Repair regresses structure | Reject stale earlier architecture pass |
| Lint or compile defect | Report and repair or skip correctly |
| Agent failure or check error | Stop without pretending the app failed |

These are test doubles, not spontaneous model results.

---

# Slide 40 — 222 tests passed. The requested artefact was still wrong.

A Microsoft preprint studied agents rebuilding a reusable UI library under a hidden suite of **222 behavioural tests**.

In one run:

```text
Behavioural tests:   222 / 222 PASS
Requested library:  unused for key behaviours
```

The demo implemented observable behaviour directly while requested reusable components were absent or non-load-bearing.

One property was green. Another had not been checked.

Source: [Ma, Kereopa-Yorke, and Schultz, *Building to the Test*](https://arxiv.org/html/2606.28430v1)

---

# Slide 41 — The local version of that lesson

```text
BUSINESS_BEHAVIOR       PASS
ARCHITECTURE_BOUNDARY   FAIL
```

The Bookshelf behaves correctly in the scenario, but domain code crosses the agreed dependency boundary.

```text
Behaviour ≠ structure
```

- JUnit checks an approved example
- ArchUnit checks one explicit dependency direction
- Neither sensor subsumes the other
- Neither one proves overall design quality

---

# Slide 42 — Deterministic rules have a boundary

Dependency rules can express:

- Forbidden imports
- Layer direction
- Package placement
- Cycles and selected structural invariants

They cannot fully judge:

- Whether responsibilities belong together
- Whether a boundary represents the domain well
- Whether duplication signals a missing concept
- Whether an exception is justified

Use computational rules where the property reduces cleanly to a rule. Use labelled inferential review for semantic concerns, with accountable human judgement.

Source: [Böckeler, *Maintainability sensors for coding agents*](https://martinfowler.com/articles/sensors-for-coding-agents.html)

---

# Slide 43 — Feedback is conditional on the oracle

Two failure modes:

1. **A bad oracle reinforces the wrong behaviour**
   - More repair iterations can optimize against a mistaken reference

2. **Inferential review can overcorrect**
   - A reviewer can reject valid code while trying to catch violations

Our response:

- Approve expectations independently of generated code
- Challenge blocking sensors with bad and valid controls
- Turn precise review concerns into reproducible checks where possible
- Keep semantic review advisory until a human resolves it

Sources: [Liang et al., *Auditing Feedback under the Oracle Problem*](https://arxiv.org/html/2608.19626v1) · [Jin and Chen, *Are LLMs reliable code reviewers?*](https://link.springer.com/article/10.1007/s10515-026-00638-5)

---

# Slide 44 — Measure outcomes separately

Record:

| Measure | Question it answers |
|---|---|
| Acceptance | Did the final attempt meet our gates? |
| Check states | Which properties passed, failed, or did not run? |
| Repair count | How much iteration did the outer loop allow? |
| Wall time | How long did this run take here? |
| Agent steps | How much interaction occurred? |
| Token categories | What did the provider report? |
| Monetary cost | What did those categories cost under known pricing? |

Do not compress them into “the harness was cheaper.” Context-file studies report different outcomes under different designs.

Sources: [Lulla et al.](https://arxiv.org/html/2601.20404v2) · [Gloaguen et al.](https://arxiv.org/html/2602.11988v2)

---

# Slide 45 — Optional: run a live coding agent

Only after the deterministic controls pass:

```bash
jbang harness/OuterHarness.java --agent=codex
```

or:

```bash
jbang harness/OuterHarness.java --agent=claude
```

The Claude adapter is pinned to `sonnet`, so both build and repair use the same
model instead of an account-specific changing default.

Observe:

- Was the final result accepted?
- How many repairs were used?
- Which checks shaped the repair?
- What elapsed time and usage were available?

A live model does not have to converge for the workshop to succeed.

---

# Slide 46 — Take the outer loop back to your company

Choose **one recurring failure** in your repository.

Fill in:

```text
Failure we repeatedly see:

Guide before action:

Sensor after action:

Known-bad control:

Known-valid control:

Repair budget and stop rule:

Evidence we will measure:

Decision that still needs human approval:
```

Be concrete enough that your team could try the first version next week.

---

# Slide 47 — Choose the right cadence

| When | Suitable controls |
|---|---|
| Every agent attempt | Compile, focused lint, targeted tests, explicit dependency rules |
| Clean CI environment | Full regression, integration, security and policy gates |
| Periodically or after risky change | Mutation testing, dependency freshness, security review, semantic modularity review |

Ask for every candidate sensor:

- Is it fast enough at this cadence?
- Is its output actionable?
- What does a failure really establish?
- How will we test the sensor itself?
- Who may approve an exception?

---

# Slide 48 — What you built

```text
approved intent
      ↓
agent attempt
      ↓
named, independent findings
      ↓
bounded repair or explicit stop
      ↓
fresh final evidence
      ↓
ACCEPTED or UNRESOLVED
```

You did more than run an agent.

You engineered the conditions under which its work could be evaluated, repaired, and accepted.

---

# Slide 49 — Five ideas to remember

1. An agent is a model inside a harness
2. Guides shape action; sensors inspect results
3. Computational checks only prove the properties they encode
4. Repair feedback must be current, compact, and calibrated
5. Acceptance belongs to the final source snapshot

```text
Human-owned intent.
Machine-executable evidence.
Explicit acceptance.
```

---

# Slide 50 — Sources used in the main story

- [Birgitta Böckeler — *Harness engineering for coding agent users*](https://martinfowler.com/articles/harness-engineering.html)
- [Birgitta Böckeler — *Maintainability sensors for coding agents*](https://martinfowler.com/articles/sensors-for-coding-agents.html)
- [Merrill et al. — *Terminal-Bench*](https://arxiv.org/html/2601.11868v1)
- [Haeri and Ghelichi — *Specification Grounding Drives Test Effectiveness for LLM Code*](https://arxiv.org/html/2607.06636v1)
- [Ma, Kereopa-Yorke, and Schultz — *Building to the Test*](https://arxiv.org/html/2606.28430v1)
- [Lahiri — *Intent Formalization*](https://arxiv.org/html/2603.17150v1)
- [Liang et al. — *Auditing Feedback under the Oracle Problem*](https://arxiv.org/html/2608.19626v1)
- [Jin and Chen — *Are LLMs reliable code reviewers?*](https://link.springer.com/article/10.1007/s10515-026-00638-5)
- [Lulla et al. — *Impact of AGENTS.md on the Efficiency of AI Coding Agents*](https://arxiv.org/html/2601.20404v2)
- [Gloaguen et al. — *Evaluating AGENTS.md*](https://arxiv.org/html/2602.11988v2)
