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

The starter already gives you `shelf`, `service` and `aliceId`, and imports both
`BorrowOutcome` and `assertEquals`. Replace the unasserted borrow and the comments with:

```java
var bobId = 11;

assertEquals(BorrowOutcome.BORROWED, service.borrow(1, aliceId));
assertEquals(BorrowOutcome.BOOK_UNAVAILABLE, service.borrow(1, bobId));
assertEquals((long) aliceId, shelf.activeLoan(1).memberId());

assertEquals(BorrowOutcome.RETURNED, service.returnBook(1));
assertEquals(BorrowOutcome.BORROWED, service.borrow(1, bobId));
assertEquals((long) bobId, shelf.activeLoan(1).memberId());
```

```bash
./mvnw -pl bookshelf test                              # now FAILS on the starter defect
./mvnw -pl harness test -Dtest=OracleCalibrationTest      # both controls green
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
for (var spec : List.of(Checks.STATIC_HYGIENE, Checks.BUSINESS_BEHAVIOR,
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
        .append("Fix all of them in one pass.\n");
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

The behaviour suite reads this prompt back and fails scenario 2 unless it contains the
policy and all three failing check names. Collect only the first failure and you spend
your whole budget fixing a third of the problem.

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
HARNESS VERIFICATION: STOP — finish and calibrate BorrowPolicyTest first
```

Your borrowing check still passes against a known defect. Finish section 3. A loop
sitting on an uncalibrated oracle cannot be certified, so the suite declines to try.

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

## A live run behaves differently

```bash
./harness.sh live claude
```

Expected. A live model may finish with `repairs=0` because it got it right first time, or
with `repairs=1`, or not converge at all. None of that means your loop is wrong — your
loop is judged by the six deterministic scenarios, which is precisely why they exist.
