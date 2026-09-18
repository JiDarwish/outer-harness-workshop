# Harness engineering: build the outer loop around a coding agent

A 90 minute workshop. The coding agent you already use (e.g. claude code) is the **inner harness**. You will
build the **outer** one: the guide it gets before it acts, the sensors that run after, the
repair budget, and the rule for what counts as a yes.

## Prerequisites

The core workshop requires **Java 25**. The final live workflow also requires either
the Claude Code CLI or the Codex CLI to be installed and signed in; everything before
that capstone runs without a model account.

```bash
java -version          # must report 25
```

Using SDKMAN? This repository ships a `.sdkmanrc`, so you can just run:

```bash
sdk env                # switches this shell to Java 25
```

On Windows, if you cannot run `harness.sh` then see [individual_commands.md](individual_commands.md) for the commands you can run.

Before the workshop, run all four commands once with a network connection. Together
they cache the compiler, test runner, linter, and harness-launch dependencies:

```bash
./mvnw test-compile
./mvnw -pl bookshelf test
./mvnw -pl bookshelf checkstyle:check
./harness.sh check
```

The Bookshelf test passes at this point: that false green starts section 1. The final
command exits 1 with `UNCHECKED` because you have not wired the outer harness yet. Both
results are expected.

## The four commands

| What you are doing | Command |
|---|---|
| Run the Bookshelf's own tests | `./mvnw -pl bookshelf test` |
| Run your outer loop against the real Bookshelf | `./harness.sh check` |
| Check your outer-loop decisions against six supplied scenarios | `./mvnw -pl harness test` |
| Run the complete workflow with Claude Code | `./harness.sh live claude` |

The live adapters deliberately use older models: Claude `haiku` and Codex `gpt-5.5`. You can change those in `CliAgent.java` if you want but they should be enough.

## The two halves

```
bookshelf/    the system under test.   The agent edits this.
harness/      the outer loop.          The agent never sees this.
```

They are separate Maven modules, and `harness` declares no dependency on `bookshelf`. It
reaches the Bookshelf only as a directory of source text and by running Maven on it. That
separation is not tidiness — it is the reason your checks count as independent evidence.

## What you should see before you start

On a fresh clone the suite is supposed to be red, in one specific way:

```bash
./mvnw -pl harness test
```

- `BorrowPolicyControlTest` fails on the bad control — your borrowing check passes against
  a known defect. That is the false green, and finding it is section 1 of the guide.
- `OuterLoopBehaviourTest` stops immediately with
  `HARNESS VERIFICATION: STOP — replace the approved-policy placeholder first`.

If that is what you see, your environment is correct.

## Now open [GUIDE.md](GUIDE.md)

One section per lab, and each starts with the idea before the task.
[SOLUTION.md](SOLUTION.md) is the safety net if you get stuck; reach for it per section,
not all at once.
