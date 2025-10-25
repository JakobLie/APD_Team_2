package org.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class ProgressTrackerTask implements Runnable {
    private final int totalUsers;
    private AtomicInteger passwordsFound;
    private AtomicInteger tasksCompleted;

    public ProgressTrackerTask(int totalUsers, AtomicInteger passwordsFound, AtomicInteger tasksCompleted) {
        this.totalUsers = totalUsers;
        this.passwordsFound = passwordsFound;
        this.tasksCompleted = tasksCompleted;
    }

    @Override
    public void run() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int lastReported = 0; // Track last checkpoint

        while (tasksCompleted.get() < totalUsers) {
            int currentTasks = tasksCompleted.get();
            
            // Calculate the current checkpoint rounded to lowest thousand (e.g., 1000, 2000, 3000...)
            int currentCheckpoint = (currentTasks / 1000) * 1000;

            //if (tasksCompleted.get() % 1000 == 0 && tasksCompleted.get() > 0) {
            if (currentCheckpoint > lastReported && currentCheckpoint > 0) {
                double progressPercent = (double) tasksCompleted.get() / totalUsers * 100;
                String timestamp = LocalDateTime.now().format(formatter);

                System.out.printf("\r[%s] %.2f%% complete | Passwords Found: %d | Tasks Remaining: %d",
                        timestamp, progressPercent, passwordsFound.get(), totalUsers - tasksCompleted.get());
                System.out.flush();

                lastReported = currentCheckpoint;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (tasksCompleted.get() % 1000 == 0 || tasksCompleted.get() == totalUsers) {
            double progressPercent = (double) tasksCompleted.get() / totalUsers * 100;
            String timestamp = LocalDateTime.now().format(formatter);

            System.out.printf("\r[%s] %.2f%% complete | Passwords Found: %d | Tasks Remaining: %d",
                    timestamp, progressPercent, passwordsFound.get(), totalUsers - tasksCompleted.get());
        }
    }
}
