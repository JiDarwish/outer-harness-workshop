#!/usr/bin/env bash
# Facilitator demo: behavior stays green while the supplied architecture rule fails.
set -u
cd "$(dirname "$0")"

target=bookshelf/src/main/java/workshop/bookshelf/domain/Book.java
backup=$(mktemp)
service=bookshelf/src/main/java/workshop/bookshelf/service/BorrowService.java
service_backup=$(mktemp)
bad_log=$(mktemp)
cp "$target" "$backup"
cp "$service" "$service_backup"
trap 'cp "$backup" "$target"; cp "$service_backup" "$service"' EXIT

cp fixtures/good/BorrowService.java "$service"
cp fixtures/architecture-bad/Book.java "$target"
if (cd bookshelf && ./mvnw -q -B -Dtest=BorrowServiceTest,BorrowPolicyTest test >"$bad_log" 2>&1); then
    printf 'BEHAVIOR: PASS with domain depending on storage\n'
else
    printf 'BEHAVIOR: FAIL unexpectedly\n'
    tail -12 "$bad_log"
    exit 1
fi

if (cd bookshelf && ./mvnw -q -B -Dtest=ArchitectureTest test >"$bad_log" 2>&1); then
    printf 'ARCHITECTURE: PASS — rule missed the dependency\n'
    exit 1
else
    printf 'ARCHITECTURE: FAIL — rule detects the dependency\n'
fi

cp "$backup" "$target"
if (cd bookshelf && ./mvnw -q -B -Dtest=ArchitectureTest test >"$bad_log" 2>&1); then
    printf 'VALID CONTROL: PASS\n'
else
    printf 'VALID CONTROL: FAIL unexpectedly\n'
    tail -12 "$bad_log"
    exit 1
fi
