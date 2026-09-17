# Harness engineering: build the outer loop around a coding agent

A 95-minute workshop. The coding agent you already use is the **inner harness**. You will
build the **outer** one: the guide it gets before it acts, the checks that run after, the
repair budget, and the rule for what counts as a yes.

## Prerequisites

**Java 25** and **Git**. That is all — there is no other tool to install.

```bash
java -version          # must report 25
```

Using SDKMAN? This repository ships a `.sdkmanrc`, so you can just run:

```bash
sdk env                # switches this shell to Java 25
```

Then warm the dependency cache — the only step that needs the network:

```bash
./mvnw test-compile     # about a minute the first time
```

**Do this before you arrive.**

Java 25 may print native-access or `Unsafe` deprecation warnings while Maven starts.
Use Maven's final result and the named check states as your checkpoint.

## The four commands

| What you are doing | Command |
|---|---|
| Run the Bookshelf's own tests | `./mvnw -pl bookshelf test` |
| Run your outer loop against the real Bookshelf | `./harness.sh check` |
| Prove your loop handles all six behaviours | `./mvnw -pl harness test` |
| Optional: drive a real coding agent | `./harness.sh live claude` |

The optional adapters deliberately use older models: Claude `haiku` and Codex
`gpt-5.5`. The deterministic workshop path does not require either CLI or a model login.

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

- `OracleCalibrationTest` fails on the bad control — your borrowing check passes against
  a known defect. That is the false green, and finding it is section 1 of the guide.
- `OuterLoopBehaviourTest` stops immediately with
  `HARNESS VERIFICATION: STOP — replace the approved-policy placeholder first`.

If that is what you see, your environment is correct.

## Now open [GUIDE.md](GUIDE.md)

One section per lab, and each starts with the idea before the task.
[SOLUTION.md](SOLUTION.md) is the safety net if you get stuck; reach for it per section,
not all at once.
