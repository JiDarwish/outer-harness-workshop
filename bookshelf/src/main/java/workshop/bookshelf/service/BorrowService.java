package workshop.bookshelf.service;

import workshop.bookshelf.domain.BorrowOutcome;
import workshop.bookshelf.domain.Loan;
import workshop.bookshelf.storage.InMemoryBookshelf;

/** This starter has a known borrowing-policy defect. The existing tests still pass. */
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
