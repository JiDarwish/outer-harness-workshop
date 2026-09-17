#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")"

java_version=$(java -version 2>&1 | head -1)
case "$java_version" in
    *'"25.'*) printf 'Java 25: ok\n' ;;
    *) printf 'Java 25 required; found %s\n' "$java_version"; exit 1 ;;
esac
command -v git >/dev/null || { printf 'Git missing\n'; exit 1; }
command -v jbang >/dev/null || { printf 'JBang missing\n'; exit 1; }
printf 'Git and JBang: ok\n'

preflight_log=$(mktemp)
trap 'rm -f "$preflight_log"' EXIT
if ! (cd bookshelf && ./mvnw -q -B test) >"$preflight_log" 2>&1; then
    tail -20 "$preflight_log"
    exit 1
fi
printf 'Bookshelf baseline tests: green\n'
if ! java harness/StaticHygiene.java bookshelf/src/main/java >"$preflight_log" 2>&1; then
    tail -20 "$preflight_log"
    exit 1
fi
printf 'Static hygiene sensor: ok\n'
if ! jbang build harness/OuterHarness.java >"$preflight_log" 2>&1; then
    tail -20 "$preflight_log"
    exit 1
fi
printf 'Outer harness compilation: ok\n'

if command -v claude >/dev/null || command -v codex >/dev/null; then
    printf 'Coding agent CLI found; make sure you are signed in\n'
else
    printf 'No coding agent CLI found; the deterministic workshop path still works\n'
fi
