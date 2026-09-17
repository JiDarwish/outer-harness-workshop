#!/usr/bin/env bash
# Prove the attendee-owned BorrowPolicyTest distinguishes a known bad state
# from a known good one. The production file is restored on exit.
set -u
cd "$(dirname "$0")"

target=bookshelf/src/main/java/workshop/bookshelf/service/BorrowService.java
backup=$(mktemp)
bad_log=$(mktemp)
good_log=$(mktemp)
cp "$target" "$backup"
cleanup() {
    cp "$backup" "$target"
    rm -f "$backup" "$bad_log" "$good_log"
}
trap cleanup EXIT

cp fixtures/bad/BorrowService.java "$target"
if (cd bookshelf && ./mvnw -q -B -Dtest=BorrowPolicyTest test >"$bad_log" 2>&1); then
    printf 'BAD CONTROL: PASS — the new check still misses the defect\n'
    bad_rejected=0
else
    printf 'BAD CONTROL: FAIL — the new check detects the defect\n'
    bad_rejected=1
fi

cp fixtures/good/BorrowService.java "$target"
if (cd bookshelf && ./mvnw -q -B -Dtest=BorrowPolicyTest test >"$good_log" 2>&1); then
    printf 'GOOD CONTROL: PASS — the check accepts valid borrowing\n'
    good_accepted=1
else
    printf 'GOOD CONTROL: FAIL — the check rejects valid borrowing\n'
    tail -12 "$good_log"
    good_accepted=0
fi

if [ "$bad_rejected" -eq 1 ] && [ "$good_accepted" -eq 1 ]; then
    printf 'CONTROL PAIR: PASS\n'
else
    printf 'CONTROL PAIR: FAIL\n'
    exit 1
fi
