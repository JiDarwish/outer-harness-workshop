#!/usr/bin/env bash
# Deterministically test the participant's outer loop in disposable copies.
set -u
cd "$(dirname "$0")"

printf 'Verifying the outer harness with scripted agents in disposable copies.\n'
printf 'No live model is used and the working production sources are not changed.\n'

scratch=$(mktemp -d "${TMPDIR:-/tmp}/bookshelf-verify-harness.XXXXXX")
cp -R bookshelf "$scratch/bookshelf"
cp -R harness "$scratch/harness"
cp -R fixtures "$scratch/fixtures"
cp controls.sh "$scratch/controls.sh"

# A placeholder guide is not an approved expectation. This cannot judge the
# stakeholder's rule, but it can reject the untouched starter text.
if grep -Fq "Write the outcome agreed with the librarian here" \
        "$scratch/bookshelf/approved-policy.md"; then
    printf 'HARNESS VERIFICATION: STOP — replace the approved-policy placeholder first\n'
    printf 'Disposable files: %s\n' "$scratch"
    exit 1
fi

# The loop cannot certify a false-green business oracle. Calibrate it first.
if ! (cd "$scratch" && bash controls.sh) >"$scratch/oracle.log" 2>&1; then
    cat "$scratch/oracle.log"
    printf 'HARNESS VERIFICATION: STOP — finish and calibrate BorrowPolicyTest first\n'
    printf 'Disposable files: %s\n' "$scratch"
    exit 1
fi
cat "$scratch/oracle.log"

failures=0
case_number=0
case_total=11

expect() {
    if ! grep -Fq "$2" "$1"; then
        printf '  missing: %s\n' "$2"
        return 1
    fi
}

run_case() {
    local mode="$1" expected_status="$2" expected_repairs="$3" expected_exit="$4"
    local case_root log exit_code ok
    case_number=$((case_number + 1))
    printf '[%s/%s] %s\n' "$case_number" "$case_total" "$mode"
    case_root=$(mktemp -d "$scratch/$mode.XXXXXX")
    cp -R "$scratch/bookshelf" "$case_root/bookshelf"
    cp -R "$scratch/harness" "$case_root/harness"
    cp -R "$scratch/fixtures" "$case_root/fixtures"
    log="$case_root/output.log"
    if [ "$mode" = "check-error" ]; then
        chmod -x "$case_root/bookshelf/mvnw"
        mode=good-first
    fi
    (cd "$case_root" && jbang harness/OuterHarness.java "--agent=$mode") >"$log" 2>&1
    exit_code=$?
    ok=1
    if [ "$exit_code" -ne "$expected_exit" ]; then
        printf '  exit: expected %s, got %s\n' "$expected_exit" "$exit_code"
        ok=0
    fi
    expect "$log" "status=$expected_status repairs=$expected_repairs" || ok=0
    case "$1" in
        good-first)
            for stage in COMPILE STATIC_HYGIENE BUSINESS_BEHAVIOR ARCHITECTURE_BOUNDARY FULL_TEST_SUITE; do
                expect "$log" "check=$stage state=PASS attempt=build" || ok=0
            done
            local build_prompt
            build_prompt=$(sed -n '/^\[SCRIPTED good-first attempt=1 received\]/,/^attempt=build/p' "$log")
            if [[ "$build_prompt" != *"Approved policy:"* ]]; then
                printf '  initial prompt omitted the approved policy\n'
                ok=0
            fi
            ;;
        noop)
            expect "$log" "check=BUSINESS_BEHAVIOR state=FAIL attempt=build" || ok=0
            expect "$log" "check=ARCHITECTURE_BOUNDARY state=PASS attempt=build" || ok=0
            expect "$log" "check=FULL_TEST_SUITE state=SKIPPED attempt=build" || ok=0
            expect "$log" "check=BUSINESS_BEHAVIOR state=FAIL attempt=repair" || ok=0
            expect "$log" "check=ARCHITECTURE_BOUNDARY state=PASS attempt=repair" || ok=0
            expect "$log" "check=FULL_TEST_SUITE state=SKIPPED attempt=repair" || ok=0
            ;;
        fix-on-repair)
            expect "$log" "check=BUSINESS_BEHAVIOR state=FAIL attempt=build" || ok=0
            for stage in COMPILE STATIC_HYGIENE BUSINESS_BEHAVIOR ARCHITECTURE_BOUNDARY FULL_TEST_SUITE; do
                expect "$log" "check=$stage state=PASS attempt=repair" || ok=0
            done
            ;;
        structure-bad)
            expect "$log" "check=BUSINESS_BEHAVIOR state=PASS attempt=build" || ok=0
            expect "$log" "check=ARCHITECTURE_BOUNDARY state=FAIL attempt=build" || ok=0
            expect "$log" "check=FULL_TEST_SUITE state=SKIPPED attempt=repair" || ok=0
            ;;
        combined-fixed)
            expect "$log" "check=BUSINESS_BEHAVIOR state=FAIL attempt=build" || ok=0
            expect "$log" "check=ARCHITECTURE_BOUNDARY state=FAIL attempt=build" || ok=0
            expect "$log" "check=FULL_TEST_SUITE state=SKIPPED attempt=build" || ok=0
            for stage in BUSINESS_BEHAVIOR ARCHITECTURE_BOUNDARY FULL_TEST_SUITE; do
                expect "$log" "check=$stage state=PASS attempt=repair" || ok=0
            done
            local prompt
            prompt=$(sed -n '/^\[SCRIPTED combined-fixed attempt=2 received\]/,/^attempt=repair/p' "$log")
            if [[ "$prompt" != *"Approved policy:"* || "$prompt" != *BUSINESS_BEHAVIOR*
                  || "$prompt" != *ARCHITECTURE_BOUNDARY* ]]; then
                printf '  repair prompt omitted one of the two focused failures\n'
                ok=0
            fi
            ;;
        regress-structure)
            expect "$log" "check=ARCHITECTURE_BOUNDARY state=PASS attempt=build" || ok=0
            expect "$log" "check=BUSINESS_BEHAVIOR state=PASS attempt=repair" || ok=0
            expect "$log" "check=ARCHITECTURE_BOUNDARY state=FAIL attempt=repair" || ok=0
            expect "$log" "check=FULL_TEST_SUITE state=SKIPPED attempt=repair" || ok=0
            ;;
        lint-bad)
            expect "$log" "check=STATIC_HYGIENE state=FAIL attempt=build" || ok=0
            expect "$log" "check=BUSINESS_BEHAVIOR state=PASS attempt=build" || ok=0
            expect "$log" "check=ARCHITECTURE_BOUNDARY state=PASS attempt=build" || ok=0
            expect "$log" "check=FULL_TEST_SUITE state=SKIPPED attempt=repair" || ok=0
            ;;
        compile-fixed)
            expect "$log" "check=COMPILE state=FAIL attempt=build" || ok=0
            for stage in STATIC_HYGIENE BUSINESS_BEHAVIOR ARCHITECTURE_BOUNDARY FULL_TEST_SUITE; do
                expect "$log" "check=$stage state=SKIPPED attempt=build" || ok=0
                expect "$log" "check=$stage state=PASS attempt=repair" || ok=0
            done
            ;;
        agent-fail)
            expect "$log" "agent=scripted agent failure" || ok=0
            expect "$log" "check=COMPILE state=SKIPPED attempt=build" || ok=0
            ;;
        repair-fail)
            expect "$log" "check=BUSINESS_BEHAVIOR state=FAIL attempt=build" || ok=0
            expect "$log" "agent=scripted agent failure" || ok=0
            expect "$log" "check=COMPILE state=SKIPPED attempt=repair" || ok=0
            ;;
        check-error)
            expect "$log" "check=COMPILE state=ERROR attempt=build" || ok=0
            expect "$log" "check=STATIC_HYGIENE state=SKIPPED attempt=build" || ok=0
            if grep -Fq "attempt=repair" "$log"; then
                printf '  check ERROR incorrectly triggered a repair\n'
                ok=0
            fi
            ;;
    esac
    if [ "$ok" -eq 1 ]; then
        printf '  PASS\n'
    else
        printf '  FAIL — output in %s\n' "$log"
        failures=$((failures + 1))
    fi
}

run_case good-first ACCEPTED 0 0
run_case noop UNRESOLVED 1 1
run_case fix-on-repair ACCEPTED 1 0
run_case structure-bad UNRESOLVED 1 1
run_case combined-fixed ACCEPTED 1 0
run_case regress-structure UNRESOLVED 1 1
run_case lint-bad UNRESOLVED 1 1
run_case compile-fixed ACCEPTED 1 0
run_case agent-fail UNRESOLVED 0 1
run_case repair-fail UNRESOLVED 1 1
run_case check-error UNRESOLVED 0 1

if [ "$failures" -eq 0 ]; then
    printf 'HARNESS CONTROL SUITE: PASS\n'
else
    printf 'HARNESS CONTROL SUITE: FAIL (%s cases)\n' "$failures"
    printf 'Disposable files: %s\n' "$scratch"
    exit 1
fi
