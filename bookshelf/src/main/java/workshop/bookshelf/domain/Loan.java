package workshop.bookshelf.domain;

/** An active loan. Returning a book removes its loan from the in-memory shelf. */
public record Loan(long bookId, long memberId) { }
