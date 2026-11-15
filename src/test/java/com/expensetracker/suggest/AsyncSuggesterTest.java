package com.expensetracker.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AsyncSuggesterTest {

    private static final List<String> CATEGORIES = List.of("Food", "Transport");

    @Test
    void deliversResultOnTheUiExecutor() throws InterruptedException {
        List<Optional<String>> results = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        try (AsyncSuggester async = new AsyncSuggester((d, c) -> Optional.of("Food"),
                task -> { task.run(); done.countDown(); })) {
            async.request("lunch", CATEGORIES, results::add);
            assertTrue(done.await(2, TimeUnit.SECONDS));
        }
        assertEquals(List.of(Optional.of("Food")), results);
    }

    @Test
    void staleResponses_areDropped_onlyLatestIsDelivered() throws InterruptedException {
        // The first call is slow; while it runs the user types more, sending two newer requests.
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        List<String> results = new CopyOnWriteArrayList<>();

        CategorySuggester slowThenFast = (description, categories) -> {
            if (description.equals("ub")) {
                firstStarted.countDown();
                try {
                    releaseFirst.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Optional.of("Food"); // wrong, stale answer
            }
            return Optional.of("Transport");
        };

        try (AsyncSuggester async = new AsyncSuggester(slowThenFast, task -> { task.run(); delivered.countDown(); })) {
            async.request("ub", CATEGORIES, r -> results.add("ub:" + r.orElse("")));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            async.request("uber", CATEGORIES, r -> results.add("uber:" + r.orElse("")));
            async.request("uber to town", CATEGORIES, r -> results.add("uber to town:" + r.orElse("")));
            releaseFirst.countDown();
            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            Thread.sleep(100); // give any (wrong) extra deliveries a chance to show up
        }
        assertEquals(List.of("uber to town:Transport"), results);
    }

    @Test
    void cancelPending_dropsTheResult() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Optional<String>> results = new CopyOnWriteArrayList<>();
        AsyncSuggester async = new AsyncSuggester((d, c) -> {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Optional.of("Food");
        }, Runnable::run);

        async.request("lunch", CATEGORIES, results::add);
        assertTrue(started.await(2, TimeUnit.SECONDS));
        async.cancelPending(); // user picked a category by hand
        release.countDown();
        async.close();

        assertTrue(results.isEmpty());
    }

    @Test
    void close_interruptsAStuckCallAndReturnsPromptly() {
        AsyncSuggester async = new AsyncSuggester((d, c) -> {
            try {
                Thread.sleep(60_000); // e.g. a long retry backoff
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }, Runnable::run);
        async.request("lunch", CATEGORIES, r -> { });

        long start = System.nanoTime();
        async.close();
        long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
        assertTrue(seconds < 5, "shutdown took " + seconds + "s");
    }
}
