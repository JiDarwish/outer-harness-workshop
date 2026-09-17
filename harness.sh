#!/usr/bin/env bash
# The only script in this repository. It wraps Maven and nothing else.
#
#   ./harness.sh check          Run your outer loop against the real Bookshelf.
#                               No agent is invoked and no repair is requested.
#   ./harness.sh live claude    Run it against a real coding agent (optional).
#   ./harness.sh live codex
#
# The harness exits 1 when the outcome is not ACCEPTED. That is the loop refusing
# to accept, which is the whole point — it is not the build breaking.
set -u
cd "$(dirname "$0")"

case "${1:-check}" in
    check) args="--check-only" ;;
    live)  args="--agent=${2:?usage: ./harness.sh live claude|codex}" ;;
    *)     echo "usage: ./harness.sh [check | live claude|codex]" >&2; exit 2 ;;
esac

./mvnw -pl harness compile exec:java \
    -Dexec.args="$args" -Dexec.cleanupDaemonThreads=false
exit $?
