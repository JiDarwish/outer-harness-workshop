package workshop.bookshelf.domain;

import workshop.bookshelf.storage.InMemoryBookshelf;

/** Deliberate structural violation: a domain concept now knows its storage adapter. */
public record Book(long id, String title) {
    public boolean existsIn(InMemoryBookshelf shelf) {
        return shelf.hasBook(id);
    }
}
