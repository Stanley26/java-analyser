package com.legacy.analyzer.cli.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple, thread-safe console progress bar.
 */
public class ConsoleProgressBar {

    private final String taskName;
    private final long total;
    private final AtomicInteger current = new AtomicInteger(0);
    private final int barWidth = 50;

    public ConsoleProgressBar(String taskName, long total) {
        this.taskName = taskName;
        this.total = total;
    }

    /**
     * Starts the progress bar display.
     */
    public void start() {
        // Only show the bar in an interactive console to avoid cluttering log files
        if (System.console() != null) {
            update(0);
        }
    }

    /**
     * Advances the progress bar by one step. This method is thread-safe.
     */
    public void step() {
        int currentProgress = current.incrementAndGet();
        if (System.console() != null) {
            // We use a synchronized block to prevent multiple threads from
            // interleaving their write operations to System.out.
            synchronized (this) {
                update(currentProgress);
            }
        }
    }

    private void update(int currentProgress) {
        // Avoid division by zero
        float percent = (total == 0) ? 1.0f : ((float) currentProgress / total);
        int completedWidth = (int) (barWidth * percent);

        StringBuilder sb = new StringBuilder();
        sb.append('\r'); // Carriage return to overwrite the line
        sb.append(taskName).append(" : [");
        for (int i = 0; i < barWidth; i++) {
            if (i < completedWidth) {
                sb.append('█');
            } else {
                sb.append('░');
            }
        }
        sb.append("] ")
          .append(String.format("%d%%", (int) (percent * 100)))
          .append(" (").append(currentProgress).append("/").append(total).append(")");

        System.out.print(sb.toString());
    }

    /**
     * Finishes the progress bar, moving the cursor to the next line.
     */
    public void close() {
        if (System.console() != null) {
            // Ensure the bar shows 100% on close
            if (current.get() < total) {
                update((int)total);
            }
            System.out.println();
        }
    }
}