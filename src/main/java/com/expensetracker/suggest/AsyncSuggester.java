package com.expensetracker.suggest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Runs suggestions on a background worker thread so the UI never freezes during a network call.
 *
 * Race condition handled: the user keeps typing, so several requests can be in flight and
 * finish out of order (a slow answer for "ub" arriving after the answer for "uber to town").
 * Every request gets an increasing sequence number and only the latest one's result is
 * delivered; stale results are dropped.
 *
 * Results are handed to {@code uiExecutor} (Platform::runLater in the app), because JavaFX
 * controls may only be touched from the JavaFX thread.
 *
 * Backpressure: a single worker thread processes requests one at a time, and together with
 * the UI's typing debounce this bounds how fast requests are created.
 */
public class AsyncSuggester implements AutoCloseable {

    private final CategorySuggester suggester;
    private final java.util.concurrent.Executor uiExecutor;
    private final ExecutorService worker;
    private final AtomicLong latestRequest = new AtomicLong();

    public AsyncSuggester(CategorySuggester suggester, java.util.concurrent.Executor uiExecutor) {
        this(suggester, uiExecutor, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "category-suggester");
            thread.setDaemon(true); // never keeps the JVM alive on its own
            return thread;
        }));
    }

    AsyncSuggester(CategorySuggester suggester, java.util.concurrent.Executor uiExecutor, ExecutorService worker) {
        this.suggester = suggester;
        this.uiExecutor = uiExecutor;
        this.worker = worker;
    }

    /** Asks for a suggestion; {@code onResult} runs on the UI executor, only if this is still the latest request. */
    public void request(String description, List<String> categories, Consumer<Optional<String>> onResult) {
        long id = latestRequest.incrementAndGet();
        List<String> snapshot = List.copyOf(categories); // don't share a mutable list across threads
        worker.execute(() -> {
            if (id != latestRequest.get()) {
                return; // superseded before it even started: skip the call entirely
            }
            Optional<String> result = suggester.suggest(description, snapshot);
            uiExecutor.execute(() -> {
                if (id == latestRequest.get()) {
                    onResult.accept(result);
                }
            });
        });
    }

    /** Drops any pending result, e.g. when the user picks a category themselves. */
    public void cancelPending() {
        latestRequest.incrementAndGet();
    }

    /**
     * Graceful shutdown: stop accepting work, give an in-flight call a moment to finish,
     * then interrupt it (which also cuts short any retry backoff sleep).
     */
    @Override
    public void close() {
        cancelPending();
        worker.shutdown();
        try {
            if (!worker.awaitTermination(2, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
