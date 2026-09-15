# Harness Engineering: build the loop around your coding agent

This repository is the 95-minute solo workshop starter. Clone it, then work from
the directory containing this README and `bookshelf/`. The Bookshelf is small:
one `Book` is one physical copy, a `Member` borrows it, and a `Loan` records who holds
it. There is no server or database. The existing tests pass, but the borrowing policy
is not yet checked. That false green is where the exercise begins.

The **inner harness** is Claude Code or Codex: it reads code and makes a change. The
Java file [`harness/OuterHarness.java`](harness/OuterHarness.java) is your **outer
harness**: it prepares the agent's input, calls it, runs a check independently, offers
one repair attempt, and reports the result. You will implement those decisions in the
loop. Expect about two dozen Java lines across the policy test and loop. The CLI
adapters and process plumbing are supplied.

| | Ordinary computation | Model interpretation |
|---|---|---|
| Before the change (feedforward) | Your initial build prompt includes the approved policy. | The agent interprets that policy while editing. |
| After the change (feedback) | JUnit and ArchUnit check declared properties. | A supplied advisory review raises semantic concerns. |

Code can guarantee that a guide is delivered; it cannot guarantee how a model
interprets it. A passing test proves its own expectation was met for the cases
it ran, not that the expectation covers everything stakeholders meant.

## Before the session

Have Java 25, JBang, Git, and either `claude` or `codex` installed and signed in. A
Maven wrapper is included. From the repository root run:

```bash
bash preflight.sh
```

The first run may download Maven, JUnit, ArchUnit, and JBang dependencies; do it before
arriving. The no-op path and deterministic checks work without a coding-agent login.
If your agent is unavailable during the session, follow the projected live run and
still do the local check exercise.

## Solo exercise

1. Run `(cd bookshelf && ./mvnw -B test)` from the repository root. It passes.
   The tests are in the familiar `bookshelf/src/test/java` directory. Read
   [`BorrowService.java`](bookshelf/src/main/java/workshop/bookshelf/service/BorrowService.java)
   and [`BorrowPolicyTest.java`](bookshelf/src/test/java/workshop/bookshelf/BorrowPolicyTest.java).
   Why is the green test suite not evidence for a second borrow?
2. Replace the questions in
   [`bookshelf/approved-policy.md`](bookshelf/approved-policy.md) with the borrowing
   outcome agreed with the librarian. Do not ask the coding agent to choose it for you.
3. In `BorrowPolicyTest`, keep a reference to the shelf. Assert Bob's rejected
   borrow result **and** that Alice remains the active borrower. After Alice returns
   the book, assert that Bob can borrow it and becomes the active borrower. The
   `shelf.activeLoan(1).memberId()` query makes the stored state visible. Run
   `(cd bookshelf && ./mvnw -B -Dtest=BorrowPolicyTest test)` from the root;
   the known-bad starter should fail. Run `bash controls.sh` from the root to
   prove your test rejects the bad implementation and accepts the good one.
   The script restores your production code afterward.
4. In `harness/OuterHarness.java`, complete LAB 1 and LAB 2: put the approved
   policy in `prompt`, then make `check(root)` call `Checks.run(root)`. Run
   `jbang harness/OuterHarness.java --agent=noop`. This supplied test double
   changes nothing. You should see your policy, one failed check, and
   `status=UNRESOLVED repairs=0`; the command exits nonzero deliberately.
5. Complete LAB 3 and LAB 4 in the same file. If the check failed and the latest
   agent attempt succeeded, permit **one** repair. Send `finding.detail()` and
   `finding.rerun()` in the repair prompt; record the new `agent.build(...)` result,
   rerun `check(root)`, and show it. Accept only when the final finding is
   `PASS` and no agent attempt failed. Rerun the no-op command. It should now
   show two failing checks, one repair prompt, and
   `status=UNRESOLVED repairs=1`.
6. Run `jbang harness/OuterHarness.java --agent=claude` or use `--agent=codex`. The
   agent receives your policy in its prompt and a temporary copy of production
   sources. It cannot read or edit your tests. The
   outer loop checks its result and may request one repair. The command stops an agent
   call after three minutes and reports `UNRESOLVED` if the work is not accepted.
   Compare accepted outcome, elapsed time, and available token counts; a faster or
   cheaper run that misses the policy is not an equivalent success. Providers
   account for cached input differently, so do not add the displayed categories
   together without checking that CLI's usage semantics.

If you get stuck, [`help/solution.md`](help/solution.md) contains the
completed test and loop edits. You are done when the bad control fails, the good
control passes, and your no-op loop makes one bounded repair attempt and reports
`UNRESOLVED`. A live model repair does not have to converge.

## Why the other sensor is supplied

Run `bash architecture-control.sh` to see a separate failure: behavior tests still
pass, but a domain class imports storage code and the supplied ArchUnit rule rejects
it. Attendees do not need to code ArchUnit. It is a fast, deterministic check of a
specific dependency direction; it does not know whether borrowing policy is right.
The reverse is true of the borrowing test. An additional check may prevent rework, but
can also increase total agent time or tokens when it triggers repair.

The facilitator will show a labelled advisory AI review and live repair examples.
Treat them as discussion evidence, not results your own run must reproduce.

## Files worth opening

- [`bookshelf/src/main/java`](bookshelf/src/main/java): the small production app.
- [`bookshelf/src/test/java`](bookshelf/src/test/java): JUnit policy and architecture
  tests. The coding agent receives only `bookshelf/src/main/java`.
- [`harness/OuterHarness.java`](harness/OuterHarness.java): the loop you extend.
- [`harness/Agent.java`](harness/Agent.java) and [`harness/Checks.java`](harness/Checks.java):
  supplied CLI and subprocess plumbing. Read them later if you want the mechanics.
