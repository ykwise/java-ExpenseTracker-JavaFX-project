package com.expensetracker.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LlmCategorySuggesterTest {

    private static final List<String> CATEGORIES = List.of("Food", "Other", "Transport");

    /** Replays scripted responses (status -1 = network failure) and records each call. */
    static final class FakeTransport implements HttpTransport {
        final Deque<Response> script = new ArrayDeque<>();
        final List<String> bodies = new ArrayList<>();

        FakeTransport then(int status, String text) {
            script.add(new Response(status, "{\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]}",
                    Optional.empty()));
            return this;
        }

        FakeTransport thenStatus(int status, Long retryAfter) {
            script.add(new Response(status, "{}", Optional.ofNullable(retryAfter)));
            return this;
        }

        FakeTransport thenNetworkError() {
            script.add(new Response(-1, "", Optional.empty()));
            return this;
        }

        @Override
        public Response post(java.net.URI uri, java.util.Map<String, String> headers, String body,
                             Duration timeout) throws IOException {
            bodies.add(body);
            Response next = script.poll();
            if (next == null || next.status() == -1) {
                throw new IOException("connection reset");
            }
            return next;
        }
    }

    private final List<Duration> sleeps = new ArrayList<>();
    private final CircuitBreakerTest.ManualClock clock = new CircuitBreakerTest.ManualClock();
    private final CircuitBreaker breaker = new CircuitBreaker(2, Duration.ofMinutes(1), clock);

    private LlmCategorySuggester suggester(FakeTransport transport) {
        return new LlmCategorySuggester("test-key", "test-model", transport,
                new RetryPolicy(3, Duration.ofMillis(500), Duration.ofSeconds(4), new Random(1)),
                breaker, sleeps::add);
    }

    @Test
    void validAnswer_isReturned() {
        FakeTransport t = new FakeTransport().then(200, "Transport");
        assertEquals(Optional.of("Transport"), suggester(t).suggest("uber", CATEGORIES));
    }

    @Test
    void answerIsNormalised_caseAndPunctuation() {
        FakeTransport t = new FakeTransport().then(200, " food. ");
        assertEquals(Optional.of("Food"), suggester(t).suggest("lunch", CATEGORIES));
    }

    @Test
    void hallucinatedCategory_isRejected() {
        FakeTransport t = new FakeTransport().then(200, "Groceries and household");
        assertEquals(Optional.empty(), suggester(t).suggest("lunch", CATEGORIES));
    }

    @Test
    void transientErrors_areRetriedWithBackoff() {
        FakeTransport t = new FakeTransport().thenStatus(529, null).thenNetworkError().then(200, "Food");

        assertEquals(Optional.of("Food"), suggester(t).suggest("lunch", CATEGORIES));
        assertEquals(3, t.bodies.size());
        assertEquals(2, sleeps.size());
        assertTrue(sleeps.get(0).toMillis() <= 500 && sleeps.get(1).toMillis() <= 1000, sleeps.toString());
    }

    @Test
    void retryAfterHeader_isHonoured() {
        FakeTransport t = new FakeTransport().thenStatus(429, 2L).then(200, "Food");
        suggester(t).suggest("lunch", CATEGORIES);
        assertEquals(List.of(Duration.ofSeconds(2)), sleeps);
    }

    @Test
    void retryAfterLongerThanMaxDelay_givesUpInsteadOfWaiting() {
        FakeTransport t = new FakeTransport().thenStatus(429, 60L).then(200, "Food");
        assertEquals(Optional.empty(), suggester(t).suggest("lunch", CATEGORIES));
        assertTrue(sleeps.isEmpty());
        assertEquals(1, t.bodies.size());
    }

    @Test
    void clientErrors_areNotRetried() {
        FakeTransport t = new FakeTransport().thenStatus(401, null).then(200, "Food");
        assertEquals(Optional.empty(), suggester(t).suggest("lunch", CATEGORIES));
        assertEquals(1, t.bodies.size());
    }

    @Test
    void stopsAfterMaxAttempts() {
        FakeTransport t = new FakeTransport().thenStatus(500, null).thenStatus(500, null)
                .thenStatus(500, null).then(200, "Food");
        assertEquals(Optional.empty(), suggester(t).suggest("lunch", CATEGORIES));
        assertEquals(3, t.bodies.size());
    }

    @Test
    void openCircuit_skipsTheNetworkEntirely() {
        FakeTransport t = new FakeTransport().thenStatus(401, null).thenStatus(401, null).then(200, "Food");
        LlmCategorySuggester s = suggester(t);
        s.suggest("a", CATEGORIES);
        s.suggest("b", CATEGORIES); // second failure trips the breaker (threshold 2)

        assertEquals(Optional.empty(), s.suggest("lunch", CATEGORIES));
        assertEquals(2, t.bodies.size(), "no request should be sent while the circuit is open");

        clock.advance(Duration.ofMinutes(1));
        assertEquals(Optional.of("Food"), s.suggest("lunch", CATEGORIES)); // half-open trial succeeds
    }

    @Test
    void requestBody_escapesUserInputAndListsCategories() {
        String body = suggester(new FakeTransport()).requestBody("Tea \"special\"\nbreak", CATEGORIES);
        assertTrue(body.contains("\"content\":\"Tea \\\"special\\\"\\nbreak\""), body);
        assertTrue(body.contains("Food, Other, Transport"), body);
        assertTrue(body.contains("\"model\":\"test-model\""), body);
    }
}
