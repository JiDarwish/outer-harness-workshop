package workshop.bookshelf.domain;

/** One physical copy, not a title shared by several copies. */
public record Book(long id, String title) { }
