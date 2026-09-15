package workshop.bookshelf;

import org.junit.jupiter.api.Test;
import workshop.bookshelf.service.BorrowService;

/** Attendee-owned expectation. Add the agreed result before asking the agent to repair. */
class BorrowPolicyTest {
    @Test
    void aSecondMemberCannotBorrowAnAlreadyLoanedBook() {
        var service = new BorrowService(BorrowServiceTest.shelf());
        service.borrow(1, 10);

        // Workshop edit: keep a shelf reference to inspect activeLoan(1).
        // Assert Bob's result and that Alice still holds the book.
        // Then return it, let Bob borrow it, and assert who holds it afterward.
        // This test currently passes without checking any of that: a false green.
    }
}
