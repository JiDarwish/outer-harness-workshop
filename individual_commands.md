# Running the workshop without `harness.sh`

Use this page when you are on Windows or when you prefer to invoke Maven directly.
`harness.sh` is only a small wrapper around the Maven commands shown below.

## Windows: use WSL for the complete workshop

The complete workshop works in **Windows Subsystem for Linux (WSL)**. Install Java 25
and the optional Claude Code or Codex CLI inside WSL, then run all commands from the WSL
terminal.

```bash
java -version                    # must report 25
sdk env                          # optional, if you installed Java with SDKMAN
```

Cloning the repository under your WSL home directory, such as `~/workshops`, normally
gives better filesystem performance than working under `/mnt/c`.

## Direct command equivalents

Run these commands from the repository root.

| Purpose | Direct Maven command |
|---|---|
| Warm the Maven compiler cache | `./mvnw test-compile` |
| Run the Bookshelf tests | `./mvnw -pl bookshelf test` |
| Run the Bookshelf linter | `./mvnw -pl bookshelf checkstyle:check` |
| Check the current Bookshelf without an agent | `./mvnw -pl harness compile exec:java -Dexec.args=--check-only -Dexec.cleanupDaemonThreads=false` |
| Run the six supplied outer-loop decision scenarios | `./mvnw -pl harness test` |
| Run one control-pair test class | `./mvnw -pl harness test -Dtest=BorrowPolicyControlTest` |
| Run the complete workflow with Claude Code | `./mvnw -pl harness compile exec:java -Dexec.args=--agent=claude -Dexec.cleanupDaemonThreads=false` |
| Run the complete workflow with Codex | `./mvnw -pl harness compile exec:java -Dexec.args=--agent=codex -Dexec.cleanupDaemonThreads=false` |

These pairs are equivalent:

```text
./harness.sh check
    =
./mvnw -pl harness compile exec:java -Dexec.args=--check-only -Dexec.cleanupDaemonThreads=false

./harness.sh live claude
    =
./mvnw -pl harness compile exec:java -Dexec.args=--agent=claude -Dexec.cleanupDaemonThreads=false
```
