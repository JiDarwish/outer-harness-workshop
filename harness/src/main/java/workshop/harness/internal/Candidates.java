package workshop.harness.internal;

/**
 * The candidate source trees the scripted agent hands back, named for what they DO
 * rather than for which defect they contain.
 *
 * <p>Keeping them here as text means one file to read instead of a directory tree to
 * cross-reference, and it lets the scripted scenarios read like sentences.
 *
 * <p>Participants never need to open this file.
 */
public record Candidates(String borrowService, String book) {

    /** Everything holds. The agent got it right first time. */
    public static Candidates valid() {
        return new Candidates(SERVICE_VALID, BOOK_VALID);
    }

    /** A second borrow silently replaces the first active loan. Behaviour fails, nothing else. */
    public static Candidates missesSecondBorrowRule() {
        return new Candidates(SERVICE_MISSES_RULE, BOOK_VALID);
    }

    /** Lint violation, missing borrowing rule, and a domain class reaching into storage. */
    public static Candidates violatesAllThree() {
        return new Candidates(SERVICE_VIOLATES_ALL, BOOK_REACHES_INTO_STORAGE);
    }

    /** A syntax defect, so nothing downstream can produce a verdict. */
    public static Candidates doesNotCompile() {
        return new Candidates(SERVICE_DOES_NOT_COMPILE, BOOK_VALID);
    }

    /** Behaviour is correct now, but the structural boundary has been broken instead. */
    public static Candidates fixesBehaviourBreaksBoundary() {
        return new Candidates(SERVICE_VALID, BOOK_REACHES_INTO_STORAGE);
    }

    private static final String SERVICE_VALID = """
            package workshop.bookshelf.service;

            import workshop.bookshelf.domain.BorrowOutcome;
            import workshop.bookshelf.domain.Loan;
            import workshop.bookshelf.storage.InMemoryBookshelf;

            /** Valid control for the participant's borrowing-policy check. */
            public final class BorrowService {
                private final InMemoryBookshelf shelf;

                public BorrowService(InMemoryBookshelf shelf) {
                    this.shelf = shelf;
                }

                public BorrowOutcome borrow(long bookId, long memberId) {
                    if (!shelf.hasBook(bookId)) return BorrowOutcome.BOOK_NOT_FOUND;
                    if (!shelf.hasMember(memberId)) return BorrowOutcome.MEMBER_NOT_FOUND;
                    if (shelf.activeLoan(bookId) != null) return BorrowOutcome.BOOK_UNAVAILABLE;

                    shelf.record(new Loan(bookId, memberId));
                    return BorrowOutcome.BORROWED;
                }

                public BorrowOutcome returnBook(long bookId) {
                    if (shelf.activeLoan(bookId) == null) return BorrowOutcome.NOT_ON_LOAN;
                    shelf.returnBook(bookId);
                    return BorrowOutcome.RETURNED;
                }
            }
            """;

    private static final String SERVICE_MISSES_RULE = """
            package workshop.bookshelf.service;

            import workshop.bookshelf.domain.BorrowOutcome;
            import workshop.bookshelf.domain.Loan;
            import workshop.bookshelf.storage.InMemoryBookshelf;

            /** Known-bad control: a second borrow silently replaces the first active loan. */
            public final class BorrowService {
                private final InMemoryBookshelf shelf;

                public BorrowService(InMemoryBookshelf shelf) {
                    this.shelf = shelf;
                }

                public BorrowOutcome borrow(long bookId, long memberId) {
                    if (!shelf.hasBook(bookId)) return BorrowOutcome.BOOK_NOT_FOUND;
                    if (!shelf.hasMember(memberId)) return BorrowOutcome.MEMBER_NOT_FOUND;

                    shelf.record(new Loan(bookId, memberId));
                    return BorrowOutcome.BORROWED;
                }

                public BorrowOutcome returnBook(long bookId) {
                    if (shelf.activeLoan(bookId) == null) return BorrowOutcome.NOT_ON_LOAN;
                    shelf.returnBook(bookId);
                    return BorrowOutcome.RETURNED;
                }
            }
            """;

    private static final String SERVICE_VIOLATES_ALL = """
            package workshop.bookshelf.service;

            import workshop.bookshelf.domain.BorrowOutcome;
            import workshop.bookshelf.domain.Loan;
            import workshop.bookshelf.storage.InMemoryBookshelf;

            /** Deliberately violates lint and the approved borrowing policy. */
            public final class BorrowService {
                private final InMemoryBookshelf shelf;

                public BorrowService(InMemoryBookshelf shelf) {
                    this.shelf = shelf;
                }

                public BorrowOutcome borrow(long bookId, long memberId) {
                    if (!shelf.hasBook(bookId)) return BorrowOutcome.BOOK_NOT_FOUND;
                    if (!shelf.hasMember(memberId)) return BorrowOutcome.MEMBER_NOT_FOUND;

                    try {
                        shelf.record(new Loan(bookId, memberId));
                    } catch (RuntimeException failure) {
                    }
                    return BorrowOutcome.BORROWED;
                }

                public BorrowOutcome returnBook(long bookId) {
                    if (shelf.activeLoan(bookId) == null) return BorrowOutcome.NOT_ON_LOAN;
                    shelf.returnBook(bookId);
                    return BorrowOutcome.RETURNED;
                }
            }
            """;

    private static final String SERVICE_DOES_NOT_COMPILE = """
            package workshop.bookshelf.service;

            import workshop.bookshelf.domain.BorrowOutcome;
            import workshop.bookshelf.domain.Loan;
            import workshop.bookshelf.storage.InMemoryBookshelf;

            /** Deliberate syntax defect for the compile-prerequisite control. */
            public final class BorrowService {
                private final InMemoryBookshelf shelf;

                public BorrowService(InMemoryBookshelf shelf) {
                    this.shelf = shelf;
                }

                public BorrowOutcome borrow(long bookId, long memberId) {
                    if (!shelf.hasBook(bookId)) return BorrowOutcome.BOOK_NOT_FOUND;
                    if (!shelf.hasMember(memberId)) return BorrowOutcome.MEMBER_NOT_FOUND;
                    if (shelf.activeLoan(bookId) != null) return BorrowOutcome.BOOK_UNAVAILABLE
                    shelf.record(new Loan(bookId, memberId));
                    return BorrowOutcome.BORROWED;
                }

                public BorrowOutcome returnBook(long bookId) {
                    if (shelf.activeLoan(bookId) == null) return BorrowOutcome.NOT_ON_LOAN;
                    shelf.returnBook(bookId);
                    return BorrowOutcome.RETURNED;
                }
            }
            """;

    private static final String BOOK_VALID = """
            package workshop.bookshelf.domain;

            /** One physical copy, not a title shared by several copies. */
            public record Book(long id, String title) { }
            """;

    private static final String BOOK_REACHES_INTO_STORAGE = """
            package workshop.bookshelf.domain;

            import workshop.bookshelf.storage.InMemoryBookshelf;

            /** Deliberate structural violation: a domain concept now knows its storage adapter. */
            public record Book(long id, String title) {
                public boolean existsIn(InMemoryBookshelf shelf) {
                    return shelf.hasBook(id);
                }
            }
            """;
}
