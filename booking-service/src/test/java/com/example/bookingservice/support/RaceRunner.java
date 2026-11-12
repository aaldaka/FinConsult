package com.example.bookingservice.support;

import com.example.bookingservice.exception.InformationNotFound;
import com.example.bookingservice.exception.InvalidRequest;
import com.example.bookingservice.exception.SlotUnavailable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class RaceRunner {

    public record Result(int successes, int refused, int other) {}

    private RaceRunner() {
    }

    // every task gets its own thread, all are released together, and each one is counted by how it ended
    public static Result runAtOnce(List<Runnable> tasks) throws Exception {
        int threads = tasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);   // every thread is in place
        CountDownLatch go = new CountDownLatch(1);            // released together
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        for (Runnable task : tasks) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    task.run();
                    successes.incrementAndGet();
                } catch (Throwable e) {
                    if (isRefusal(e)) {
                        refused.incrementAndGet();
                    } else {
                        e.printStackTrace();
                        other.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        go.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "tasks did not finish in time");
        pool.shutdown();
        return new Result(successes.get(), refused.get(), other.get());
    }

    // a clean no: a business rule, or the database or a version check saying no (possibly wrapped by Spring)
    private static boolean isRefusal(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof InvalidRequest || t instanceof SlotUnavailable || t instanceof InformationNotFound
                    || t instanceof DataIntegrityViolationException
                    || t instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }
        }
        return false;
    }
}
