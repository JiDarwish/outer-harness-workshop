#!/usr/bin/env bash
# Final control suite for the participant's outer loop. demo-repair.sh is the
# visible teaching path; this script challenges that loop in disposable copies.
set -u
cd "$(dirname "$0")"

printf 'Verifying the outer harness with six scenarios in disposable copies.\n'
printf 'No live model is used and the working production sources are not changed.\n'
printf 'This is the final control suite; use demo-repair.sh to study one repair in full.\n'

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
case_total=6

expect() {
    if ! grep -Fq "$2" "$1"; then
        printf '  missing: %s\n' "$2"
        return 1
    fi
}

run_case() {
    local scenario="$1" label="$2" expected_status="$3" expected_repairs="$4" expected_exit="$5"
    local case_root log exit_code ok agent_mode
    case_number=$((case_number + 1))
    printf '[%s/%s] %s\n' "$case_number" "$case_total" "$label"
    case_root=$(mktemp -d "$scratch/$scenario.XXXXXX")
    cp -R "$scratch/bookshelf" "$case_root/bookshelf"
    cp -R "$scratch/harness" "$case_root/harness"
    cp -R "$scratch/fixtures" "$case_root/fixtures"
    log="$case_root/output.log"
    agent_mode="$scenario"
    if [ "$scenario" = "check-error" ]; then
        chmod -x "$case_root/bookshelf/mvnw"
        agent_mode=valid-first
    fi
    (cd "$case_root" && jbang harness/OuterHarness.java "--agent=$agent_mode") >"$log" 2>&1
    exit_code=$?
    ok=1
    if [ "$exit_code" -ne "$expected_exit" ]; then
        printf '  exit: expected %s, got %s\n' "$expected_exit" "$exit_code"
        ok=0
    fi
    expect "$log" "status=$expected_status repairs=$expected_repairs" || ok=0

    case "$scenario" in
        valid-first)
            for stage in COMPILE STATIC_HYGIENE BUSINESS_BEHAVIOR ARCHITECTURE_BOUNDARY FULL_TEST_SUITE; do
                expect "$log" "check=$stage state=PASS attempt=build" || ok=0
            done
            local build_prompt
            build_prompt=$(sed -n '/^\[SCRIPTED valid-first attempt=1 received\]/,/^attempt=build/p' "$log")
            if [[ "$build_prompt" != *"Approved policy:"* ]]; then
                printf '  initial prompt omitted the approved policy\n'
                ok=0
            fi
            ;;
        demo)
            expect "$log" "check=COMPILE state=PASS attempt=build" || ok=0
            for stage in STATIC_HYGIENE BUSINESS_BEHAVIOR ARCHITECTURE_BOUNDARY; do
                expect "$log" "check=$stage state=FAIL attempt=build" || ok=0
            done
            expect "$log" "check=FULL_TEST_SUITE state=SKIPPED attempt=build" || ok=0
            for stage in COMPILE STATIC_HYGIENE BUSINESS_BEHAVIOR ARCHITECTURE_BOUNDARY FULL_TEST_SUITE; do
                expect "$log" "check=$stage state=PASS attempt=repair" || ok=0
            done
            local repair_prompt
            repair_prompt=$(sed -n '/^\[SCRIPTED demo attempt=2 received\]/,/^attempt=repair/p' "$log")
            if [[ "$repair_prompt" != *"Approved policy:"*
                  || "$repair_prompt" != *STATIC_HYGIENE*
                  || "$repair_prompt" != *BUSINESS_BEHAVIOR*
                  || "$repair_prompt" != *ARCHITECTURE_BOUNDARY* ]]; then
                printf '  repair prompt omitted policy or an independent failure\n'
                ok=0
            fi
            ;;
        compile-gate)
            expect "$log" "check=COMPILE state=FAIL attempt=build" || ok=0
            for stage in STATIC_HYGIENE BUSINESS_BEHAVIOR ARCHITECTURE_BOUNDARY FULL_TEST_SUITE; do
                expect "$log" "check=$stage state=SKIPPED attempt=build" || ok=0
                expect "$log" "check=$stage state=PASS attempt=repair" || ok=0
            done
            ;;
        repair-regression)
            expect "$log" "check=BUSINESS_BEHAVIOR state=FAIL attempt=build" || ok=0
            expect "$log" "check=ARCHITECTURE_BOUNDARY state=PASS attempt=build" || ok=0
            expect "$log" "check=BUSINESS_BEHAVIOR state=PASS attempt=repair" || ok=0
            expect "$log" "check=ARCHITECTURE_BOUNDARY state=FAIL attempt=repair" || ok=0
            expect "$log" "check=FULL_TEST_SUITE state=SKIPPED attempt=repair" || ok=0
            ;;
        agent-failure)
            expect "$log" "agent=scripted agent failure" || ok=0
            for stage in COMPILE STATIC_HYGIENE BUSINESS_BEHAVIOR ARCHITECTURE_BOUNDARY FULL_TEST_SUITE; do
                expect "$log" "check=$stage state=SKIPPED attempt=build" || ok=0
            done
            ;;
        check-error)
            expect "$log" "check=COMPILE state=ERROR attempt=build" || ok=0
            for stage in STATIC_HYGIENE BUSINESS_BEHAVIOR ARCHITECTURE_BOUNDARY FULL_TEST_SUITE; do
                expect "$log" "check=$stage state=SKIPPED attempt=build" || ok=0
            done
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

run_case valid-first 'Accept a valid first attempt' ACCEPTED 0 0
run_case demo 'Repair all independent failures' ACCEPTED 1 0
run_case compile-gate 'Stop dependent checks after compilation fails' ACCEPTED 1 0
run_case repair-regression 'Reject a regression introduced by repair' UNRESOLVED 1 1
run_case agent-failure 'Stop when the agent produces no candidate' UNRESOLVED 0 1
run_case check-error 'Do not repair broken check infrastructure' UNRESOLVED 0 1

if [ "$failures" -eq 0 ]; then
    rm -rf "$scratch"
    printf 'HARNESS CONTROL SUITE: PASS\n'
else
    printf 'HARNESS CONTROL SUITE: FAIL (%s cases)\n' "$failures"
    printf 'Disposable files: %s\n' "$scratch"
    exit 1
fi
