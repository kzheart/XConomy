package me.yic.xconomy.utils;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Ordered, bounded worker owned by the plugin rather than Bukkit's task pool. */
public final class OrderedWriter implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final Consumer<Throwable> errors;

    public OrderedWriter(String thread, int capacity, Consumer<Throwable> errors) {
        this.errors = errors;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), runnable -> {
            Thread worker = new Thread(runnable, thread); worker.setDaemon(false); return worker;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public void submit(Runnable action) {
        if (failure.get() != null) throw new RejectedExecutionException("Storage writer failed", failure.get());
        executor.execute(() -> {
            if (failure.get() != null) return;
            try { action.run(); }
            catch (Throwable error) { if (failure.compareAndSet(null, error)) errors.accept(error); }
        });
    }

    public <T> java.util.concurrent.CompletableFuture<T> call(java.util.function.Supplier<T> action) {
        java.util.concurrent.CompletableFuture<T> result=new java.util.concurrent.CompletableFuture<>();
        if(failure.get()!=null){result.completeExceptionally(new RejectedExecutionException("Storage writer failed",failure.get()));return result;}
        try{executor.execute(()->{
            if(failure.get()!=null){result.completeExceptionally(new RejectedExecutionException("Earlier storage write failed",failure.get()));return;}
            try{result.complete(action.get());}catch(Throwable error){if(failure.compareAndSet(null,error))errors.accept(error);result.completeExceptionally(error);}
        });}catch(RuntimeException error){result.completeExceptionally(error);}return result;
    }

    public int pending() { return executor.getQueue().size() + executor.getActiveCount(); }

    public boolean healthy() { return failure.get() == null; }

    public void flush() {
        if (!healthy()) throw new IllegalStateException("Storage writer failed", failure.get());
        try {
            executor.submit(() -> { }).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted draining storage", error);
        } catch (ExecutionException | java.util.concurrent.TimeoutException error) {
            throw new IllegalStateException("Storage barrier failed", error);
        }
        if (!healthy()) throw new IllegalStateException("Storage writer failed", failure.get());
    }

    @Override public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS))
                throw new IllegalStateException("Storage writes did not drain within 30 seconds; database must stay open");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while draining storage", error);
        }
        if (failure.get() != null) throw new IllegalStateException("Storage write failed; queued operations require reconciliation", failure.get());
    }
}
