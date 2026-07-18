package org.gw.cachesmanager.storage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class DatabaseTaskExecutor {

    private final Object lock = new Object();
    private ExecutorService databaseExecutor;

    public void ensureRunning() {
        synchronized (lock) {
            ensureRunningLocked();
        }
    }

    private void ensureRunningLocked() {
        if (databaseExecutor != null && !databaseExecutor.isShutdown() && !databaseExecutor.isTerminated()) {
            return;
        }
        shutdownQuietlyLocked(3);
        databaseExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CachesManager-DB");
            t.setDaemon(true);
            return t;
        });
    }

    public void submit(Runnable task) {
        if (task == null) {
            return;
        }
        synchronized (lock) {
            ensureRunningLocked();
            try {
                databaseExecutor.submit(task);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        if (task == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (lock) {
            ensureRunningLocked();
            try {
                return CompletableFuture.runAsync(task, databaseExecutor);
            } catch (RejectedExecutionException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        if (supplier == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (lock) {
            ensureRunningLocked();
            try {
                return CompletableFuture.supplyAsync(supplier, databaseExecutor);
            } catch (RejectedExecutionException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    public void shutdown(long timeoutSeconds) {
        synchronized (lock) {
            shutdownQuietlyLocked(timeoutSeconds);
        }
    }

    private void shutdownQuietlyLocked(long timeoutSeconds) {
        if (databaseExecutor == null) {
            return;
        }
        ExecutorService executor = databaseExecutor;
        databaseExecutor = null;
        try {
            executor.shutdown();
            if (!executor.awaitTermination(Math.max(1, timeoutSeconds), TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            try {
                executor.shutdownNow();
            } catch (Exception ignored2) {
            }
        }
    }
}
