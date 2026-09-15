package workshop.bookshelf.storage;

import workshop.bookshelf.domain.Book;
import workshop.bookshelf.domain.Loan;
import workshop.bookshelf.domain.Member;

import java.util.HashMap;
import java.util.Map;

/** Small enough to read in a minute; no database or web server is involved. */
public final class InMemoryBookshelf {
    private final Map<Long, Book> books = new HashMap<>();
    private final Map<Long, Member> members = new HashMap<>();
    private final Map<Long, Loan> activeLoans = new HashMap<>();

    public void add(Book book) { books.put(book.id(), book); }
    public void add(Member member) { members.put(member.id(), member); }
    public boolean hasBook(long id) { return books.containsKey(id); }
    public boolean hasMember(long id) { return members.containsKey(id); }
    public Loan activeLoan(long bookId) { return activeLoans.get(bookId); }
    public void record(Loan loan) { activeLoans.put(loan.bookId(), loan); }
    public void returnBook(long bookId) { activeLoans.remove(bookId); }
}
