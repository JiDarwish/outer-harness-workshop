package workshop.bookshelf.domain;

public enum BorrowOutcome {
    BORROWED,
    BOOK_NOT_FOUND,
    MEMBER_NOT_FOUND,
    BOOK_UNAVAILABLE,
    RETURNED,
    NOT_ON_LOAN
}
