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

First add the missing outcome import and static assertion import near the top:

```java
import workshop.bookshelf.domain.BorrowOutcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
```

Inside the test method, create the shelf separately so you can inspect its
active loan:

```java
var shelf = BorrowServiceTest.shelf();
var service = new BorrowService(shelf);
```

Then express the example as a sequence of observations:

```java
assertEquals(BorrowOutcome.BORROWED, service.borrow(1, 10));
assertEquals(BorrowOutcome.BOOK_UNAVAILABLE, service.borrow(1, 11));
assertEquals(10L, shelf.activeLoan(1).memberId());

assertEquals(BorrowOutcome.RETURNED, service.returnBook(1));
assertEquals(BorrowOutcome.BORROWED, service.borrow(1, 11));
assertEquals(11L, shelf.activeLoan(1).memberId());
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
    for (var spec : required()) {
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
    for (var spec : required().subList(1, required().size())) {
        findings.add(Finding.skipped(spec, "Compilation did not pass"));
    }
    return findings;
}
```

A compile `FAIL` may be repairable application evidence. A compile `ERROR`
still prevents dependent checks. `needsRepair` will distinguish those states.

### 4.3 Collect independent focused findings

Once the source compiles, run lint, business behaviour, and architecture. An
ordinary `FAIL` should not hide another useful finding. An infrastructure
`ERROR` stops later commands because the evidence chain is no longer reliable:

```java
for (var spec : List.of(
        Checks.STATIC_HYGIENE,
        Checks.BUSINESS_BEHAVIOR,
        Checks.ARCHITECTURE_BOUNDARY)) {
    if (findings.stream().anyMatch(Finding::error)) {
        findings.add(Finding.skipped(
                spec, "Earlier check had an infrastructure error"));
    } else {
        findings.add(Checks.run(root, spec));
    }
}
```

### 4.4 Gate the full regression suite

Run the complete suite only when every earlier stage passed:

```java
if (findings.stream().allMatch(Finding::passed)) {
    findings.add(Checks.run(root, Checks.REGRESSION_SUITE));
} else {
    findings.add(Finding.skipped(
            Checks.REGRESSION_SUITE,
            "A focused check did not pass"));
}
return findings;
```

Verify LAB 1 and LAB 2:

```bash
jbang harness/OuterHarness.java --agent=noop
```

Expected checkpoint:

```text
COMPILE                 PASS
STATIC_HYGIENE          PASS
BUSINESS_BEHAVIOR       FAIL
ARCHITECTURE_BOUNDARY   PASS
REGRESSION_SUITE        SKIPPED
status=UNRESOLVED repairs=0
```

The command exits `1` because rejecting the defective source is the correct
result.

## 5. LAB 3: the loop repairs the wrong situations

`needsRepair` should require a successful agent attempt, at least one
application `FAIL`, and no `ERROR` or `UNCHECKED` finding:

```java
return !report.agent().failed()
        && report.findings().stream().anyMatch(Finding::failed)
        && report.findings().stream()
                .noneMatch(finding -> finding.error() || finding.unchecked());
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

Do not send only the first failure. The combined-defect control expects both
business and architecture findings in one bounded repair request.

## 7. LAB 4: stale results are being accepted

Acceptance must inspect the final attempt as a complete ordered set. First
reject any run containing a failed agent call:

```java
if (reports.stream().anyMatch(report -> report.agent().failed())) {
    return false;
}
```

Then compare the final findings with `required()`:

```java
var finalFindings = reports.getLast().findings();
var specs = required();
if (finalFindings.size() != specs.size()) return false;

for (var index = 0; index < specs.size(); index++) {
    var finding = finalFindings.get(index);
    var spec = specs.get(index);
    if (!finding.name().equals(spec.name()) || !finding.passed()) {
        return false;
    }
}
return true;
```

This prevents an architecture `PASS` from the build attempt from approving a
repair attempt that introduced a structural violation.

## 8. LAB 5: the report hides the useful failure

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

## 9. Final deterministic checkpoint

Run the no-op once more:

```bash
jbang harness/OuterHarness.java --agent=noop
```

It should make one repair attempt, reject the unchanged defect, and finish:

```text
status=UNRESOLVED repairs=1
```

Then run the complete harness controls:

```bash
bash harness-controls.sh
```

The final checkpoint is:

```text
HARNESS CONTROL PAIR: PASS
```

The script uses disposable copies and deterministic agent fixtures. If a case
fails, read its labelled name and output path before changing the loop. Its
structure cases demonstrate that behaviour can pass while architecture fails,
including a repair that introduces a new dependency violation.

## 10. A live model behaves differently

A live agent may fix the defect on its first build, need the repair call, time
out, or fail. None of those outcomes changes the acceptance contract. Run a
live agent only after the deterministic controls pass, and record the prompt,
agent, date, elapsed time, repair count, accepted outcome, and available usage.
