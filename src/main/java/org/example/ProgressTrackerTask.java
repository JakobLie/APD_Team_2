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

        while (tasksCompleted.get() < totalUsers) {
            if (tasksCompleted.get() % 1000 == 0 && tasksCompleted.get() > 0) {
                double progressPercent = (double) tasksCompleted.get() / totalUsers * 100;
                String timestamp = LocalDateTime.now().format(formatter);

                System.out.printf("\r[%s] %.2f%% complete | Passwords Found: %d | Tasks Remaining: %d",
                        timestamp, progressPercent, passwordsFound.get(), totalUsers - tasksCompleted.get());
                System.out.flush();
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
