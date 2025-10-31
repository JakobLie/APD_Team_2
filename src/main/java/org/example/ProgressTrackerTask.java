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

        while (true) {
            int currentTasks = tasksCompleted.get();  // Read once per iteration
            
            if (currentTasks >= totalUsers) { // Each user is 1 task
                break;  // Exit before checkpoint calculation
            }
            
            int currentCheckpoint = (currentTasks / 1000) * 1000;

            if (currentCheckpoint > lastReported && currentCheckpoint > 0) {
                double progressPercent = (double) currentTasks / totalUsers * 100;
                String timestamp = LocalDateTime.now().format(formatter);

                System.out.printf("\r[%s] %.2f%% complete | Passwords Found: %d | Tasks Remaining: %d",
                        timestamp, progressPercent, passwordsFound.get(), totalUsers - currentTasks);
                System.out.flush();

                lastReported = currentCheckpoint;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        int finalTasks = tasksCompleted.get();
        double progressPercent = (double) finalTasks / totalUsers * 100;
        String timestamp = LocalDateTime.now().format(formatter);

        System.out.printf("\r[%s] %.2f%% complete | Passwords Found: %d | Tasks Remaining: %d",
                timestamp, progressPercent, passwordsFound.get(), totalUsers - finalTasks);
        System.out.flush();
    }
}
