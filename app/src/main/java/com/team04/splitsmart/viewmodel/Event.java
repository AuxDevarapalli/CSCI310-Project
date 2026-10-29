package com.team04.splitsmart.viewmodel;

/**
 * Wraps a value that should be handled only once, such as "show this message" or "close the
 * form". Without it, LiveData would deliver the same value again after a screen rotation.
 */
public class Event<T> {
    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    /** Returns the content the first time it is called, then {@code null}. */
    public T getContentIfNotHandled() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }

    /** Returns the content even if it was already handled. */
    public T peekContent() {
        return content;
    }
}
