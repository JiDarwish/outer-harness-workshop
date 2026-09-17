# Guide

Work through these in order. Each section starts with the idea, because the point of
today is not the code — it is being able to defend every decision the code makes.

---

## 1. Notice the false green

### The idea

Every decision your outer loop will make reads the output of a check. So the first
question is not "is my code correct" but **"is my check capable of telling me?"**

A suite can run, pass, and be silent about the thing you actually care about. Ma et al.
recorded 222 behavioural tests passing while the reusable components the task asked for
were never built. Nothing was broken. The suite measured a property nobody had disputed.

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

> Source: [Ma, Kereopa-Yorke and Schultz, *Building to the Test*](https://arxiv.org/abs/2606.28430)

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

## 3. Make the rule executable, and calibrate it

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
./mvnw -pl harness test -Dtest=OracleCalibrationTest
```

**Checkpoint:** both calibration tests green — your check rejects the known defect and
accepts a valid implementation. A test that still passes here is not yet a sensor.

This is cheap calibration, not mutation testing. It is no evidence that the business
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

### Do this

1. Build `prompt` from `task.md` **and** `approved-policy.md`.
2. Implement `checkSequence`:
   - no candidate from the agent → SKIP every required check, with a reason
   - `COMPILE` first; on FAIL or ERROR, SKIP the dependents and return
   - `STATIC_HYGIENE`, `BUSINESS_BEHAVIOR`, `ARCHITECTURE_BOUNDARY` independently
   - `FULL_TEST_SUITE` only if compilation and all three focused checks passed

Use `Checks.run(root, spec)` and `Finding.skipped(spec, reason)`.

```bash
./harness.sh check
```

**Checkpoint:**

```
PASS      COMPILE
PASS      STATIC_HYGIENE
FAIL      BUSINESS_BEHAVIOR
PASS      ARCHITECTURE_BOUNDARY
SKIPPED   FULL_TEST_SUITE

=== DECISION ===
UNRESOLVED
repairs=0
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

- `needsRepair`: application FAIL findings only. An agent failure, a check ERROR, or
  UNCHECKED evidence means there is no trustworthy defect to repair against.
- `repairPrompt`: **one** prompt carrying the approved policy and **every** failing
  finding — purpose, intended property, one diagnostic line, and the exact rerun command.

Three failures do not earn three repairs. They share this prompt.

There is nothing to run here: check-only never repairs, by design. The proof comes next.

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

**Checkpoint:** all six behaviours green.

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

The suite will not start until your policy placeholder is gone and your oracle is
calibrated. It declines to certify a loop whose oracle has never been challenged.

---

## 7. Optional — a live agent

```bash
./harness.sh live claude     # or: ./harness.sh live codex
```

The adapters are pinned to Claude `haiku` and Codex `gpt-5.5`. They deliberately avoid
each provider's strongest model so you can observe what the outer guidance and feedback
contribute.

A live model may finish with `repairs=0` or `repairs=1` — it may simply get it right
first time. That is why scenario 2 exists: only the deterministic path guarantees you
see the repair happen.

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
