package workshop.bookshelf;

import org.junit.jupiter.api.Test;
import workshop.bookshelf.service.BorrowService;
import workshop.bookshelf.domain.BorrowOutcome;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Attendee-owned expectation. Add the agreed result before asking the agent to repair. */
class BorrowPolicyTest {
    @Test
    void aSecondMemberCannotBorrowAnAlreadyLoanedBook() {
        var shelf = BorrowServiceTest.shelf();
        var service = new BorrowService(shelf);
        var aliceId = 10;
        service.borrow(1, aliceId);

        // Workshop edit: assert Bob's result and that Alice still holds the book.
        // Then return it, let Bob borrow it, and assert who holds it afterward.
        // This test currently passes without checking any of that: a false green.
    }
}
