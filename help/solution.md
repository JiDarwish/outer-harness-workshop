# Recovery guide

Use only the section for the checkpoint where you are stuck. Each section gives
you a small piece to compare with your code, followed by the command that proves
that piece works. Do not paste every snippet at once: the point is to recover
your next decision and continue the lab.

## 1. The borrowing rule is still unclear

The agreed rule is:

> One physical copy can have at most one active loan. When Alice holds Book 1,
> Bob's request returns `BOOK_UNAVAILABLE` and Alice remains the active borrower.
> After Alice returns it, Bob may borrow it and becomes the active borrower.

Write that outcome in your own words in:

```text
bookshelf/approved-policy.md
```

This policy is the human-approved guide. The test below is one executable
example of it.

## 2. `BorrowPolicyTest` is still green on the defective service

Work in:

```text
bookshelf/src/test/java/workshop/bookshelf/BorrowPolicyTest.java
```

The starter already imports the outcome and assertion you need:

```java
import workshop.bookshelf.domain.BorrowOutcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
```

It also gives you `shelf`, `service`, and `aliceId`. Replace the unasserted first
borrow and the workshop comments with this sequence:

```java
var bobId = 11;

assertEquals(BorrowOutcome.BORROWED, service.borrow(1, aliceId));
assertEquals(BorrowOutcome.BOOK_UNAVAILABLE, service.borrow(1, bobId));
assertEquals((long) aliceId, shelf.activeLoan(1).memberId());

assertEquals(BorrowOutcome.RETURNED, service.returnBook(1));
assertEquals(BorrowOutcome.BORROWED, service.borrow(1, bobId));
assertEquals((long) bobId, shelf.activeLoan(1).memberId());
```

Run the focused sensor:

```bash
(cd bookshelf && ./mvnw -B -Dtest=BorrowPolicyTest test)
```

The starter service should now fail because Bob receives `BORROWED` instead of
`BOOK_UNAVAILABLE`. Next challenge the sensor itself:

```bash
bash controls.sh
```

Stop here until you see:

```text
BAD CONTROL: FAIL — the new check detects the defect
GOOD CONTROL: PASS — the check accepts valid borrowing
CONTROL PAIR: PASS
```

If the good control fails, inspect the assertion failure before changing the
production service. Your test may be rejecting behaviour that the agreed valid
implementation does not promise.

## 3. LAB 1: the initial prompt does not contain the policy

Work in `harness/OuterHarness.java`. You already loaded `task` and `policy`.
Build the prompt from both values:

```java
var prompt = task + "\n\nApproved policy:\n" + policy;
```

The exact prose can differ. The important property is that the agent receives
the approved rule before it edits production code.

## 4. LAB 2: the check sequence is stuck

Work inside `checkSequence`. Build the result incrementally so every required
stage has a visible state.

### 4.1 Handle a failed agent attempt

If the agent did not produce a trustworthy source attempt, none of the checks
can describe that attempt:

```java
var findings = new ArrayList<Finding>();
if (agent.failed()) {
    for (var spec : requiredChecks()) {
        findings.add(Finding.skipped(spec, "Agent attempt failed"));
    }
    return findings;
}
```

### 4.2 Make compilation the prerequisite

Run `COMPILE`, keep its result, and make every dependent stage explicitly
`SKIPPED` if compilation does not pass:

```java
var compile = Checks.run(root, Checks.COMPILE);
findings.add(compile);
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

The named list makes the dependency visible without four repeated statements or
a list index to decode. A compile `FAIL` may be repairable application evidence.
A compile `ERROR` still prevents dependent checks. `needsRepair` will distinguish
those states.

### 4.3 Collect independent focused findings

Once the source compiles, run lint, business behaviour, and architecture. These
sensors are independent, so a `FAIL` or `ERROR` from one does not hide the
others:

```java
for (var spec : List.of(
        Checks.STATIC_HYGIENE,
        Checks.BUSINESS_BEHAVIOR,
        Checks.ARCHITECTURE_BOUNDARY)) {
    findings.add(Checks.run(root, spec));
}
```

An `ERROR` still prevents repair and acceptance later. It does not prevent an
independent sensor from producing its own evidence.

### 4.4 Gate the full test suite

The full test suite is the broad regression check: it verifies that existing
Bookshelf behaviour still works after the focused properties pass. Decide
whether every finding collected so far passed without a stream expression:

```java
var allFocusedChecksPassed = true;
for (var finding : findings) {
    if (!finding.passed()) {
        allFocusedChecksPassed = false;
    }
}

if (allFocusedChecksPassed) {
    findings.add(Checks.run(root, Checks.FULL_TEST_SUITE));
} else {
    findings.add(Finding.skipped(
            Checks.FULL_TEST_SUITE,
            "A focused check did not pass"));
}
return findings;
```

Verify LAB 1 and LAB 2:

```bash
jbang harness/OuterHarness.java --check-only
```

Expected checkpoint:

```text
COMPILE                 PASS
STATIC_HYGIENE          PASS
BUSINESS_BEHAVIOR       FAIL
ARCHITECTURE_BOUNDARY   PASS
FULL_TEST_SUITE         SKIPPED
status=UNRESOLVED repairs=0
```

The command exits `1` because rejecting the defective source is the correct
result.

## 5. LAB 3: the loop repairs the wrong situations

`needsRepair` should require a successful agent attempt, at least one
application `FAIL`, and no `ERROR` or `UNCHECKED` finding:

```java
if (report.agent().failed()) {
    return false;
}

var foundApplicationFailure = false;
for (var finding : report.findings()) {
    if (finding.error() || finding.unchecked()) {
        return false;
    }
    if (finding.failed()) {
        foundApplicationFailure = true;
    }
}
return foundApplicationFailure;
```

The surrounding main method already limits the loop to one repair. Do not
trigger application repair for an agent failure or broken check infrastructure.

## 6. LAB 3: the repair prompt lacks useful evidence

Start the repair prompt with the original task and the same approved policy:

```java
var prompt = new StringBuilder(task)
        .append("\n\nApproved policy:\n")
        .append(policy)
        .append("\nRepair production Java code only.")
        .append(" Independent failures from this attempt:\n");
```

Then append every `FAIL` from the current attempt:

```java
for (var finding : report.findings()) {
    if (!finding.failed()) continue;
    prompt.append("\n")
            .append(finding.name()).append(" — ")
            .append(finding.property()).append("\n")
            .append(finding.detail()).append("\n")
            .append("The outer harness will rerun: ")
            .append(finding.rerun()).append("\n");
}
return prompt.toString();
```

Do not send only the first failure. The combined repair scenario expects static
hygiene, business behaviour, and architecture findings in one bounded request.

## 7. LAB 4: stale results are being accepted

Acceptance must inspect the final attempt only. Old passes belong to old source
snapshots and cannot approve repaired code. Check the names as well as the
states so duplicates cannot masquerade as a complete result:

```java
var finalAttempt = reports.getLast();
if (finalAttempt.agent().failed()) {
    return false;
}

var required = requiredChecks();
if (finalAttempt.findings().size() != required.size()) {
    return false;
}

for (var index = 0; index < required.size(); index++) {
    var finding = finalAttempt.findings().get(index);
    if (!finding.name().equals(required.get(index).name()) || !finding.passed()) {
        return false;
    }
}
return true;
```

This prevents an architecture `PASS` from the build attempt from approving a
repair attempt that introduced a structural violation.

Reporting is supplied in the starter so the coding time stays focused on the
outer-loop decisions. Read the output as evidence: it shows attempts, check
states, skipped reasons, diagnostics, log paths, timing, and available usage.

## 8. Watch one complete repair

Before asking the full verification suite to challenge edge cases, run one
prepared repair where every decision remains visible:

```bash
bash demo-repair.sh
```

The script says which defects its deterministic agent will introduce in a
disposable copy. Then read the output in this order:

1. The build prompt contains the approved policy.
2. The first attempt reports static hygiene, business behaviour, and
   architecture failures independently.
3. The full suite is skipped because focused evidence did not all pass.
4. One repair prompt contains all three findings.
5. The repair reruns the entire sequence and every required check passes.
6. Fresh final evidence produces `status=ACCEPTED repairs=1`.

The checkpoint is `WALKTHROUGH: PASS`. No live model is involved, and your
working production sources are unchanged.

<!-- OPTIONAL LAB 5 — preserved for a later workshop decision.

## OPTIONAL LAB 5: report the evidence

The first line in `show` already reports the attempt and agent usage. Inside
the findings loop, print the check identity first:

```java
IO.println("check=" + finding.name()
        + " state=" + finding.state()
        + " attempt=" + report.label()
        + " elapsed_ms=" + finding.elapsedMs());
```

Then expose detail only when it helps explain a non-passing result:

```java
if (finding.failed() || finding.error()) {
    IO.println(finding.detail());
}
if (finding.state() == Finding.State.SKIPPED) {
    IO.println("reason=" + finding.detail());
}
if (finding.logPath() != null && (finding.failed() || finding.error())) {
    IO.println("full_log=" + finding.logPath());
}
```

The concise diagnostic belongs in the terminal and repair prompt. The full log
path remains available for a person who needs to investigate.

End optional LAB 5. -->

## 9. Final deterministic checkpoint

Run the deterministic harness verification suite:

```bash
bash verify-harness.sh
```

The final checkpoint is:

```text
HARNESS CONTROL SUITE: PASS
```

This is the test suite for your outer harness. It uses disposable copies and
deterministic agent fixtures, not a live model. Its six labels state the
decision being tested: accept a valid first attempt, repair all independent
failures, gate on compilation, reject a repair regression, stop on agent
failure, and distinguish broken check infrastructure. If a case fails, read
its label and output path before changing the loop.

## 10. A live model behaves differently

A live agent may fix the defect on its first build, need the repair call, time
out, or fail. None of those outcomes changes the acceptance contract. Run a
live agent only after the deterministic controls pass, and record the prompt,
agent, date, elapsed time, repair count, accepted outcome, and available usage.
The Claude adapter is pinned to `haiku`, and the Codex adapter is pinned to
`gpt-5.5`, for both attempts. These deliberately avoid each provider's
strongest model so the effect of the outer-harness guidance and feedback remains
visible.
