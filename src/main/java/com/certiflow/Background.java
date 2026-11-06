package com.certiflow;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;

/**
 * Runs network and disk work off the JavaFX event thread, then delivers UI callbacks
 * on that thread. One worker preserves the order of account and data operations.
 */
final class Background {
    private static final java.util.concurrent.ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "certiflow-io"); thread.setDaemon(true); return thread;
    });
    static <T> void run(Node busy, Callable<T> work, Consumer<T> success, Consumer<Exception> failure) {
        // Remember the originating page: a late response must not update a replacement page.
        var scene = busy.getScene();
        var root = scene == null ? null : scene.getRoot();
        busy.setDisable(true);
        IO.submit(() -> {
            T value = null; Exception error = null;
            try { value = work.call(); } catch (Exception ex) { error = ex; }
            T result = value; Exception problem = error;
            Platform.runLater(() -> {
                busy.setDisable(false);
                if (scene != null && scene.getRoot() != root) return;
                if (problem == null) success.accept(result); else failure.accept(problem);
            });
        });
    }
    // For work without a UI result; callers handle any error inside their task.
    static void execute(Runnable work) { IO.submit(work); }
}
