# Harness engineering: build the outer loop around a coding agent

This is a 95-minute solo workshop starter. Work from the directory containing this
README and `bookshelf/`. The tiny Bookshelf has one physical copy of each book. Its
existing tests pass, but the participant-owned borrowing policy test has no useful
assertions yet. That false green starts the lab.

The coding agent is the **inner harness**: it reads production Java and edits it.
You will code the **outer harness** in [`harness/OuterHarness.java`](harness/OuterHarness.java):
give the agent an agreed rule, run independent sensors after each attempt, send
bounded repair feedback, and accept only fresh final evidence. The supplied
[`Agent.java`](harness/Agent.java) and [`Checks.java`](harness/Checks.java) handle
CLI calls, isolated source copies, Maven commands, timeouts, and concise logs.

| Theory | This lab's concrete action |
|---|---|
| Guide before action | Agree the borrowing rule with the librarian, then include `bookshelf/approved-policy.md` in the build and repair prompts. |
| Sensor after action | Make `BorrowPolicyTest` effective; the outer loop invokes it after each successful agent attempt. |
| Computational versus inferential | JUnit, static lint, and ArchUnit execute declared checks. Agreeing the business expectation and reviewing semantic modularity still need judgment. |
| Independent properties | Behavior can pass while a dependency boundary fails. The outer loop records both, then checks the full suite only if focused sensors pass. |
| Fresh acceptance | A repair can fix borrowing and introduce an architecture defect. Recheck everything on the repaired source. |

The borrowing example is deliberately small. At work, the business sensor might
check pricing, permissions, time zones, or idempotency. An executable assertion
checks conformance to one example; it does not prove the stakeholder chose the
right rule or that the examples are complete.

## Before the session

Install Java 25, JBang, and Git. A Maven wrapper is included. From the repository
root run:

```bash
bash preflight.sh
```

Do this before arriving so Maven, JUnit, ArchUnit, and JBang can download. The
workshop's scripted controls need no model login. A live run at the end needs
either `claude` or `codex` installed and signed in. Native-access or SLF4J
warnings can appear even when Maven tests pass; use the test result and control
labels as your checkpoint.

## Code the lab, one checkpoint at a time

1. **Notice the false green.** Run `(cd bookshelf && ./mvnw -B test)` from the
   repository root. It passes. Open
   [`BorrowService.java`](bookshelf/src/main/java/workshop/bookshelf/service/BorrowService.java)
   and [`BorrowPolicyTest.java`](bookshelf/src/test/java/workshop/bookshelf/BorrowPolicyTest.java).
   The tests live under the familiar `bookshelf/src/test/java` Maven layout.
   Ask: what happens when Bob requests the copy Alice holds, and why did no test
   catch it?

2. **Approve the rule before the agent acts.** Replace the questions in
   [`bookshelf/approved-policy.md`](bookshelf/approved-policy.md) with the
   librarian's agreed outcome. This is the feedforward guide. The agent should
   implement the rule, not decide the rule for you.

3. **Make that rule testable.** In `BorrowPolicyTest`, keep a shelf reference.
   Assert Bob's result and the active borrower while Alice holds Book 1. After
   Alice returns it, assert Bob can borrow and becomes the active borrower.
   Run `(cd bookshelf && ./mvnw -B -Dtest=BorrowPolicyTest test)`; the starter
   defect should fail. Then run `bash controls.sh` from the root. The checkpoint
   is `BAD CONTROL: FAIL`, `GOOD CONTROL: PASS`, `CONTROL PAIR: PASS`. This bad/good
   pair challenges your oracle. The script restores production code afterward.

4. **Wire the outer sensors.** Complete LAB 1 and LAB 2 in
   `harness/OuterHarness.java`. Put the approved policy in the initial prompt.
   In `checkSequence`, run `COMPILE` first. If it fails or errors, mark dependent
   stages `SKIPPED`. On compilable code, run `STATIC_HYGIENE`,
   `BUSINESS_BEHAVIOR`, and `ARCHITECTURE_BOUNDARY` independently; an ordinary
   `FAIL` must not hide another focused result. Run `REGRESSION_SUITE` only if
   all focused checks pass. Use `Checks.run(root, spec)` and
   `Finding.skipped(spec, reason)`. Run:

   ```bash
   jbang harness/OuterHarness.java --agent=noop
   ```

   The no-op agent changes nothing. At this checkpoint, business behavior is
   `FAIL`, architecture and static hygiene are `PASS`, the suite is `SKIPPED`,
   and `status=UNRESOLVED repairs=0`. The command exits 1 on purpose. The
   unedited starter reports `UNCHECKED`.

5. **Bound repair and define acceptance.** Complete LAB 3, LAB 4, and LAB 5.
   A successful agent attempt with application `FAIL` findings may get one
   repair prompt containing **all** failing purposes, properties, short
   diagnostics, and rerun commands. Agent failure or check `ERROR` stops the
   loop. After repair, rerun the entire sequence. Accept only if every agent
   attempt succeeded and every required named check on the final attempt is
   `PASS`; show both attempts, skipped reasons, logs, timing, and available
   usage. Rerun the no-op command: it should show two business failures and
   `status=UNRESOLVED repairs=1`. Then run:

   ```bash
   bash harness-controls.sh
   ```

   It calibrates your borrowing test and runs scripted good, bad, combined,
   regressing, lint, compile, agent-failure, and check-error cases in **disposable
   copies**. It does not edit your working production sources. The checkpoint
   is `HARNESS CONTROL PAIR: PASS`. Expect about a minute on a warm local
   machine; inspect the labelled case and its output path if one fails.

6. **Try a live agent if time permits.** After the deterministic checkpoints,
   run `jbang harness/OuterHarness.java --agent=claude` or use `--agent=codex`.
   The adapter gives it an isolated copy of production Java only. It cannot
   read or edit your policy test. Each agent call has a three-minute timeout;
   acceptance is still decided by your outer loop. Compare accepted outcome,
   repair count, elapsed time, and available token categories. A missing token
   value is `unavailable`, not zero; CLI providers may count cached input
   differently, so do not add categories without checking their semantics.

If stuck, use one matching section of the
[checkpoint-based recovery guide](help/solution.md), then rerun that section's
command before reading further. The coding checkpoint is
the calibrated borrowing test plus a loop that rejects no-op and structural
regression, accepts a scripted valid repair, and rejects agent/check failures.
A live model does not have to converge for you to complete the workshop.

The supplied ArchUnit rule checks one explicit dependency direction; it cannot
judge every misplaced responsibility. The supplied static rule checks direct
console writes in this library. A passing architecture or lint check does not
certify the borrowing rule, and a passing borrowing test does not certify the
requested structure. A short advisory semantic review can raise concerns that
do not reduce cleanly to imports, with a human deciding what matters.

Before leaving, name one recurring failure in your company repository. Write
the guide you would give before an agent acts, the sensor you could run after,
one known-bad and one valid control, the repair budget, the evidence you would
measure, and the expectation that still needs human approval.
