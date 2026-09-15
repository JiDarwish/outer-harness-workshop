# Recovery guide and checks

The agreed core policy is: one physical book can have at most one active loan. Alice
may borrow Book 1; while she holds it, Bob's request for Book 1 returns
`BOOK_UNAVAILABLE` and must not replace Alice's loan. After Alice returns it, Bob may
borrow it. The starter already checks return-then-borrow but not simultaneous loans.

The attendee replaces the questions in `bookshelf/approved-policy.md` in their
own words. A concise version is:

> If Alice has an active loan for Book 1, Bob's request for Book 1 is rejected as
> `BOOK_UNAVAILABLE`, and Alice remains the active borrower. After return,
> Bob can borrow Book 1 and becomes the active borrower.

Complete `BorrowPolicyTest` with:

```java
var shelf = BorrowServiceTest.shelf();
var service = new BorrowService(shelf);
assertEquals(BorrowOutcome.BORROWED, service.borrow(1, 10));
assertEquals(BorrowOutcome.BOOK_UNAVAILABLE, service.borrow(1, 11));
assertEquals(10L, shelf.activeLoan(1).memberId());
assertEquals(BorrowOutcome.RETURNED, service.returnBook(1));
assertEquals(BorrowOutcome.BORROWED, service.borrow(1, 11));
assertEquals(11L, shelf.activeLoan(1).memberId());
```

Replace the existing method body with that block. Add
`import workshop.bookshelf.domain.BorrowOutcome;` and
`import static org.junit.jupiter.api.Assertions.assertEquals;`.

In `harness/OuterHarness.java`, replace the prompt line with:

```java
var prompt = task + "\n\nApproved policy:\n"
        + Files.readString(root.resolve("bookshelf/approved-policy.md"));
```

Replace `return Finding.notWired();` with `return Checks.run(root);`.

Below `var repairs = 0;`, add:

```java
while (finding.failed() && !attempts.getLast().failed() && repairs < 1) {
    repairs++;
    var repairPrompt = "Repair the application code. The independent check failed:\n"
            + finding.detail() + "\nThe outer harness will rerun: " + finding.rerun();
    IO.println("repair " + repairs + " via " + mode);
    attempts.add(agent.build(repairPrompt));
    finding = check(root);
    show(finding);
}
```

Replace `var accepted = false;` with:

```java
var accepted = finding.state() == Finding.State.PASS
        && attempts.stream().noneMatch(AgentResult::failed);
```

`bash controls.sh` must print `BAD CONTROL: FAIL`, `GOOD CONTROL: PASS`,
and `CONTROL PAIR: PASS`. Before adding the repair, the no-op run shows
`UNRESOLVED repairs=0`. After all four loop edits,
`jbang harness/OuterHarness.java --agent=noop` shows the failing JUnit
diagnostic twice and ends `UNRESOLVED repairs=1`. A live agent may repair on
its first build, need the second attempt, time out, or fail; do not conceal
any of those outcomes.

`bash architecture-control.sh` is the short structural comparison: borrowing tests
stay green when `Book` imports `InMemoryBookshelf`, while ArchUnit fails. The original
valid domain passes. State the limit explicitly: this rule checks imports, not the
meaning or placement of every business rule.

If a model call exceeds three minutes, stop waiting and use the control pair to finish
the exercise. Any later captured run must be labelled with its prompt, agent, date,
and usage; do not present it as a live result or as an estimate of typical behavior.
