package me.yic.xconomy.utils;

import me.yic.xconomy.XConomy;
import me.yic.xconomy.XConomyLoad;

/** Plugin lifecycle for accepted cached balance writes. */
public final class StorageWrites {
    private static OrderedWriter writer;
    private StorageWrites() { }

    public static void start() {
        if (XConomyLoad.DConfig.canasync) writer = new OrderedWriter("XConomy-storage", 10000, error -> {
            XConomy.getInstance().logger(null, 1, "CRITICAL: balance persistence failed; further writes rejected. Reconcile cached balances and database before restart.");
            error.printStackTrace();
        });
    }

    public static void submit(Runnable action) {
        if (writer == null) action.run(); else writer.submit(action);
    }

    public static void close() {
        if (writer != null) { writer.close(); writer = null; }
    }

    public static int pending() { return writer == null ? 0 : writer.pending(); }
    public static boolean healthy() { return writer == null || writer.healthy(); }

    public static void flush() { if (writer != null) writer.flush(); }
}
