package com.team04.splitsmart.data;

/**
 * Result of an asynchronous repository call. Firebase delivers results on the main thread, so
 * both methods run on the main thread and may update LiveData directly.
 *
 * @param <T> type of the successful result
 */
public interface DataCallback<T> {
    void onSuccess(T value);

    /** Called with a {@link RepositoryException} whose message can be shown to the user. */
    void onFailure(Exception e);
}
