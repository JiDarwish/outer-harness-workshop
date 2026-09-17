#!/usr/bin/env bash
# Show one complete deterministic repair loop without changing working sources.
set -u
cd "$(dirname "$0")"

printf 'VISIBLE REPAIR WALKTHROUGH\n'
printf 'This uses a prepared agent in a disposable copy, not a live model.\n'
printf 'You will see: build -> evidence -> repair prompt -> recheck -> decision.\n\n'
printf 'Prepared situation:\n'
printf '  Attempt 1 violates static hygiene, borrowing behavior, and architecture.\n'
printf '    Sources: fixtures/all-bad and fixtures/architecture-bad\n'
printf '  Your loop must collect all three findings and request one repair.\n'
printf '  Attempt 2 applies a valid implementation, then reruns every sensor.\n'
printf '    Sources: fixtures/good and fixtures/architecture-good\n\n'

scratch=$(mktemp -d "${TMPDIR:-/tmp}/bookshelf-demo-repair.XXXXXX")
cp -R bookshelf "$scratch/bookshelf"
cp -R harness "$scratch/harness"
cp -R fixtures "$scratch/fixtures"
cp controls.sh "$scratch/controls.sh"

if grep -Fq "Write the outcome agreed with the librarian here" \
        "$scratch/bookshelf/approved-policy.md"; then
    rm -rf "$scratch"
    printf 'STOP — replace the approved-policy placeholder before this walkthrough.\n'
    exit 1
fi

printf 'Checking prerequisite: the borrowing sensor rejects bad code and accepts good code.\n'
if ! (cd "$scratch" && bash controls.sh) >"$scratch/oracle.log" 2>&1; then
    cat "$scratch/oracle.log"
    printf 'STOP — finish and calibrate BorrowPolicyTest first.\n'
    printf 'Disposable files: %s\n' "$scratch"
    exit 1
fi
printf 'Prerequisite: PASS\n\n'

(cd "$scratch" && jbang harness/OuterHarness.java --agent=demo)
exit_code=$?

if [ "$exit_code" -eq 0 ]; then
    printf '\nWALKTHROUGH: PASS — the final fresh evidence was accepted.\n'
    printf 'Disposable walkthrough retained for inspection: %s\n' "$scratch"
else
    printf '\nWALKTHROUGH: FAIL — inspect the sequence above before using the full verifier.\n'
    printf 'Disposable files: %s\n' "$scratch"
fi
exit "$exit_code"
