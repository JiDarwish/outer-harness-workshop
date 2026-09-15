package workshop.bookshelf;

import org.junit.jupiter.api.Test;
import workshop.bookshelf.domain.Book;
import workshop.bookshelf.domain.BorrowOutcome;
import workshop.bookshelf.domain.Member;
import workshop.bookshelf.service.BorrowService;
import workshop.bookshelf.storage.InMemoryBookshelf;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BorrowServiceTest {
    static InMemoryBookshelf shelf() {
        var shelf = new InMemoryBookshelf();
        shelf.add(new Book(1, "The Left Hand of Darkness"));
        shelf.add(new Member(10, "Alice"));
        shelf.add(new Member(11, "Bob"));
        return shelf;
    }

    @Test
    void aMemberCanBorrowAnAvailableBook() {
        var service = new BorrowService(shelf());
        assertEquals(BorrowOutcome.BORROWED, service.borrow(1, 10));
    }

    @Test
    void aReturnedBookCanBeBorrowedAgain() {
        var service = new BorrowService(shelf());
        assertEquals(BorrowOutcome.BORROWED, service.borrow(1, 10));
        assertEquals(BorrowOutcome.RETURNED, service.returnBook(1));
        assertEquals(BorrowOutcome.BORROWED, service.borrow(1, 11));
    }
}
