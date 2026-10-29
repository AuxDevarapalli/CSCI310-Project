package com.team04.splitsmart.data;

/**
 * Failure reported by a repository. The message is already written for the user, so the
 * ViewModels never need to know which Firebase exception caused it.
 */
public class RepositoryException extends Exception {
    private static final long serialVersionUID = 1L;

    private final boolean notFound;

    public RepositoryException(String userMessage) {
        this(userMessage, false, null);
    }

    public RepositoryException(String userMessage, Throwable cause) {
        this(userMessage, false, cause);
    }

    public RepositoryException(String userMessage, boolean notFound, Throwable cause) {
        super(userMessage, cause);
        this.notFound = notFound;
    }

    /**
     * True when the requested item was deleted or the user no longer has access to it, as
     * opposed to a temporary problem such as being offline.
     */
    public boolean isNotFound() {
        return notFound;
    }

    /** Convenience check for any exception delivered to a {@link DataCallback}. */
    public static boolean isNotFound(Exception e) {
        return e instanceof RepositoryException && ((RepositoryException) e).isNotFound();
    }
}
