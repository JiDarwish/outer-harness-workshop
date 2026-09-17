package workshop.harness;

/**
 * How a check produces its verdict.
 *
 * <p>{@code MAVEN} shells out to the build; {@code LINT} is a library call. Both return
 * the same kind of Finding, because the outer loop should not care how evidence was
 * produced, only what it says.
 */
enum CheckKind { MAVEN, LINT }
